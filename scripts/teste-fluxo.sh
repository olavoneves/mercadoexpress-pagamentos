#!/usr/bin/env bash
# =====================================================================
# Teste do fluxo assincrono de pagamento via curl.
#
#   ./scripts/teste-fluxo.sh          compras aprovada e recusadas + banco direto
#   ./scripts/teste-fluxo.sh --dlq    tambem derruba o banco e mostra o retry -> DLQ
#
# Pre-requisito: docker compose up --build (ou os servicos rodando local).
# Enderecos podem ser trocados por variavel de ambiente:
#   MERCADO_URL=http://localhost:8080 BANCO_URL=http://localhost:8082 ./scripts/teste-fluxo.sh
# Nao depende de jq: o JSON e lido com grep/sed.
# =====================================================================
set -euo pipefail

MERCADO_URL="${MERCADO_URL:-http://localhost:8080}"
BANCO_URL="${BANCO_URL:-http://localhost:8082}"
RABBIT_URL="${RABBIT_URL:-http://localhost:15672}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"

verde=$'\e[32m'; amarelo=$'\e[33m'; vermelho=$'\e[31m'; negrito=$'\e[1m'; fim=$'\e[0m'

titulo() { printf '\n%s== %s ==%s\n' "$negrito" "$1" "$fim"; }
campo() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed "s/\"$1\":\"\(.*\)\"/\1/"; }
numero() { grep -o "\"$1\":[0-9.]*" | head -1 | sed "s/\"$1\"://"; }

# POST /api/compras e devolve o id da compra criada (HTTP 202)
comprar() {
    local corpo="$1" resposta codigo
    resposta=$(curl -s -w '\n%{http_code}' -X POST "$MERCADO_URL/api/compras" \
        -H 'Content-Type: application/json' -d "$corpo")
    codigo=$(tail -n1 <<<"$resposta")
    resposta=$(sed '$d' <<<"$resposta")
    if [[ "$codigo" != "202" ]]; then
        echo "${vermelho}HTTP $codigo${fim} $resposta" >&2
        return 1
    fi
    echo "  HTTP 202 Accepted -> compra $(numero id <<<"$resposta") $(campo status <<<"$resposta"), total R\$ $(numero valorTotal <<<"$resposta")" >&2
    numero id <<<"$resposta"
}

# Consulta GET /api/compras/{id} ate sair de PENDENTE
aguardar() {
    local id="$1" json status
    for _ in $(seq 1 30); do
        json=$(curl -s "$MERCADO_URL/api/compras/$id")
        status=$(campo status <<<"$json")
        if [[ "$status" != "PENDENTE" ]]; then
            local cor="$verde"; [[ "$status" == "RECUSADO" ]] && cor="$amarelo"
            sleep 1
            json=$(curl -s "$MERCADO_URL/api/compras/$id")
            echo "  -> ${cor}${status}${fim} | autorizacao: $(campo codigoAutorizacao <<<"$json") | motivo: $(campo motivo <<<"$json")"
            echo "  -> e-mail: $(grep -o '"status":"\(ENVIADO\|FALHA\)"' <<<"$json" | head -1 | cut -d'"' -f4) para $(campo clienteEmail <<<"$json")"
            return 0
        fi
        sleep 1
    done
    echo "  -> ${vermelho}continua PENDENTE apos 30s${fim}"
}

compra_json() { # nome email cpf cartao itens(JSON)
    printf '{"cliente":{"nome":"%s","email":"%s","cpf":"%s"},"pagamento":{"numeroCartao":"%s","titular":"%s"},"itens":%s}' \
        "$1" "$2" "$3" "$4" "$(tr '[:lower:]' '[:upper:]' <<<"$1")" "$5"
}

titulo "0. Servicos no ar"
for url in "$MERCADO_URL/actuator/health" "$BANCO_URL/actuator/health"; do
    printf '  %-45s %s\n' "$url" "$(curl -s "$url" | campo status || echo FORA)"
done

titulo "1. Compra APROVADA (cartao 4111..., CPF valido)"
id=$(comprar "$(compra_json 'Ana Souza' 'ana.souza@exemplo.com' '529.982.247-25' '4111 1111 1111 1111' '[{"produtoId":1,"quantidade":3},{"produtoId":6,"quantidade":2}]')")
aguardar "$id"

titulo "2. Compra RECUSADA - cartao terminado em 0000"
id=$(comprar "$(compra_json 'Bruno Lima' 'bruno.lima@exemplo.com' '529.982.247-25' '5555 4444 3333 0000' '[{"produtoId":7,"quantidade":1}]')")
aguardar "$id"

titulo "3. Compra RECUSADA - CPF bloqueado (111.111.111-11)"
id=$(comprar "$(compra_json 'Carla Mendes' 'carla.mendes@exemplo.com' '111.111.111-11' '4111 1111 1111 1111' '[{"produtoId":9,"quantidade":2}]')")
aguardar "$id"

titulo "4. Compra RECUSADA - valor acima de R\$ 5.000 (14 kits churrasco + 2 cafeteiras)"
id=$(comprar "$(compra_json 'Diego Rocha' 'diego.rocha@exemplo.com' '529.982.247-25' '4111 1111 1111 1111' '[{"produtoId":14,"quantidade":14},{"produtoId":16,"quantidade":2}]')")
aguardar "$id"

titulo "5. Chamada direta ao Banco Tranquilo (sem RabbitMQ)"
curl -s -X POST "$BANCO_URL/api/pagamentos" -H 'Content-Type: application/json' \
    -d '{"compraId":999,"valor":120.50,"cpf":"529.982.247-25","numeroCartao":"4111 1111 1111 1111","titular":"ANA SOUZA"}'
echo

titulo "6. Validacao: corpo invalido devolve 400"
curl -s -o /dev/null -w '  HTTP %{http_code}\n' -X POST "$MERCADO_URL/api/compras" -H 'Content-Type: application/json' -d '{"itens":[]}'

if [[ "${1:-}" == "--dlq" ]]; then
    titulo "7. Banco FORA do ar -> retry com backoff -> DLQ"
    docker compose stop banco-tranquilo-api >/dev/null
    id=$(comprar "$(compra_json 'Elisa Prado' 'elisa.prado@exemplo.com' '529.982.247-25' '4111 1111 1111 1111' '[{"produtoId":2,"quantidade":1}]')")
    echo "  aguardando as 5 tentativas (2s, 4s, 8s, 10s de espera)..."
    sleep 32
    docker compose logs pagamento-integracao 2>/dev/null | grep "Compra $id" | sed 's/^/  /' | cut -c1-200
    # o painel do RabbitMQ atualiza as estatisticas a cada ~5s
    for _ in $(seq 1 10); do
        na_dlq=$(curl -s -u guest:guest "$RABBIT_URL/api/queues/%2F/pagamento.solicitado.dlq" | numero messages)
        [[ "${na_dlq:-0}" -gt 0 ]] && break
        sleep 2
    done
    echo "  mensagens em pagamento.solicitado.dlq: ${vermelho}${na_dlq:-0}${fim}"
    echo "  status da compra $id: $(curl -s "$MERCADO_URL/api/compras/$id" | campo status) (fica PENDENTE ate reprocessar)"
    docker compose start banco-tranquilo-api >/dev/null
    echo "  banco religado."
fi

titulo "E-mails capturados no Mailpit ($MAILPIT_URL)"
curl -s "$MAILPIT_URL/api/v1/messages?limit=5" | grep -o '"Subject":"[^"]*"' | sed 's/"Subject":/  /'
echo
