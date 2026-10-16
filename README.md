# Mercado Express — Pagamentos com RabbitMQ

**FIAP · TDS · Checkpoint 5 — Parte II (Microsserviços e Mensageria)**
Professor Dr. Marcel Stefan Wagner · Turma 2TDSR

O Mercado Express da Parte I agora vende. O cliente monta o carrinho na
vitrine (ou manda a compra em JSON pelo Postman), a compra é gravada como
**PENDENTE** e a resposta HTTP sai na hora (**202 Accepted**). O pagamento segue
de forma **assíncrona** pelo RabbitMQ até o **Banco Tranquilo**, e o resultado
volta pela fila: a compra vira **APROVADO** ou **RECUSADO**, o estoque baixa, um
e-mail HTML é enviado ao cliente e tudo aparece no **dashboard da gerência**.

```bash
docker compose up --build
```

Esse comando sobe tudo do zero: PostgreSQL, RabbitMQ, Mailpit e os três serviços.

---

## Sumário

1. [Arquitetura](#arquitetura)
2. [Fluxo assíncrono](#fluxo-assíncrono)
3. [Exchanges, filas e routing keys](#exchanges-filas-e-routing-keys)
4. [Regras do Banco Tranquilo](#regras-do-banco-tranquilo)
5. [Criação do projeto](#criação-do-projeto)
6. [Como rodar](#como-rodar)
7. [Endpoints](#endpoints)
8. [JSON de exemplo](#json-de-exemplo)
9. [Configuração do Banco de Dados](#configuração-do-banco-de-dados)
10. [Dashboard de gerência](#dashboard-de-gerência)
11. [Controle de e-mails](#controle-de-e-mails)
12. [Resiliência: retry, DLQ e idempotência](#resiliência-retry-dlq-e-idempotência)
13. [Teste pelo painel do RabbitMQ](#teste-pelo-painel-do-rabbitmq)
14. [Testes](#testes)
15. [Estrutura do repositório](#estrutura-do-repositório)
16. [Prints](#prints)
17. [Decisões técnicas](#decisões-técnicas)
18. [Integrantes](#integrantes)

---

## Arquitetura

Os blocos seguem o diagrama do enunciado: **Front End / Back End → RabbitMQ →
Integração Pagamentos → API Plataforma de Pagamentos**, e a resposta volta
pelo mesmo broker.

```mermaid
flowchart LR
    cliente([Cliente<br/>navegador ou Postman])

    subgraph ME["mercado-express :18080<br/>Front End + Back End"]
        loja[Vitrine e carrinho<br/>Thymeleaf]
        api[API REST<br/>/api/compras]
        ger[Dashboard<br/>/gerencia]
    end

    subgraph MQ["RabbitMQ :5672 / :15672"]
        ex{{exchange topic<br/>mercado.pagamentos}}
        q1[(pagamento.solicitado)]
        q2[(pagamento.processado)]
        dlx{{mercado.pagamentos.dlx}}
        dlq[(pagamento.solicitado.dlq)]
    end

    subgraph PI["pagamento-integracao :18081<br/>Integração Pagamentos"]
        cons[Consumidor<br/>+ retry com backoff]
    end

    subgraph BT["banco-tranquilo-api :18082<br/>API Plataforma de Pagamentos"]
        banco[POST /api/pagamentos<br/>mock com regras]
    end

    pg[(PostgreSQL :5434<br/>TDS_*_MERCADO)]
    mail[Mailpit :8025<br/>ou Gmail SMTP]

    cliente --> loja & api
    loja & api -->|grava PENDENTE| pg
    api -->|publica| ex
    loja -->|publica| ex
    ex -->|pagamento.solicitado| q1 --> cons
    cons -->|RestClient| banco
    cons -->|publica resultado| ex
    ex -->|pagamento.processado| q2 -->|consome| ME
    ME -->|APROVADO/RECUSADO,<br/>baixa estoque| pg
    ME -->|e-mail HTML| mail
    q1 -.esgotou retry.-> dlx -.-> dlq
    ger --> pg
```

| Serviço | Porta | Papel |
|---|---|---|
| `mercado-express` | 18080 | Vitrine, carrinho, API de compras, consumidor do resultado, e-mail e dashboard |
| `pagamento-integracao` | 18081 | Consome `pagamento.solicitado`, chama o banco e publica `pagamento.processado` |
| `banco-tranquilo-api` | 18082 | API mock da plataforma de pagamentos |
| `mercado-commons` | — | Biblioteca: eventos, nomes das filas e topologia (`Declarables`) |
| PostgreSQL 16 | 5434 → 5432 | Banco do mercado |
| RabbitMQ 3 management | 5672 / 15672 | Broker e painel (`guest` / `guest`) |
| Mailpit | 1025 / 8025 | SMTP de teste e caixa de entrada web |

---

## Fluxo assíncrono

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant M as mercado-express
    participant DB as PostgreSQL
    participant R as RabbitMQ
    participant I as pagamento-integracao
    participant B as Banco Tranquilo
    participant S as SMTP (Mailpit)

    C->>M: POST /api/compras (ou "Finalizar compra")
    M->>DB: INSERT compra PENDENTE + itens
    M-->>C: 202 Accepted + Location /api/compras/{id}
    Note over M,R: só depois do commit (@TransactionalEventListener AFTER_COMMIT)
    M->>R: PagamentoSolicitadoEvent → pagamento.solicitado<br/>correlationId = id, persistente
    R->>I: entrega a mensagem
    I->>B: POST /api/pagamentos (RestClient)
    Note over B: espera 500–1500 ms e aplica as regras
    B-->>I: APROVADO / RECUSADO + motivo
    alt banco fora do ar
        I->>B: retenta com backoff 2s, 4s, 8s, 10s
        I->>R: esgotou → rejeita → DLX → pagamento.solicitado.dlq
    end
    I->>R: PagamentoProcessadoEvent → pagamento.processado
    R->>M: entrega o resultado
    M->>DB: SELECT ... FOR UPDATE · se ainda PENDENTE: status, código, motivo
    M->>DB: APROVADO → baixa o estoque
    M->>S: e-mail HTML com o resumo do pedido
    M->>DB: INSERT TDS_EMAILS_MERCADO (ENVIADO ou FALHA)
    C->>M: GET /api/compras/{id} → APROVADO / RECUSADO
```

A tela `/pedidos/{id}` mostra esse caminho ao vivo: enquanto a compra está
PENDENTE, ela consulta a API a cada segundo e se atualiza quando o banco responde.
Na loja: [pendente (07)](docs/07-pedido-pendente.png) →
[recusado (08)](docs/08-pedido-recusado.png) e [aprovado (10)](docs/10-pedido-aprovado.png).

---

## Exchanges, filas e routing keys

Tudo é declarado por `@Bean Declarables` em
[`mercado-commons/.../RabbitTopologiaConfig.java`](mercado-commons/src/main/java/br/com/fiap/mercadocommons/mensageria/RabbitTopologiaConfig.java),
importado pelos dois serviços que usam o broker. Os nomes são constantes em
[`RabbitNomes`](mercado-commons/src/main/java/br/com/fiap/mercadocommons/mensageria/RabbitNomes.java).

| Tipo | Nome | Routing key | Durável | Detalhes |
|---|---|---|---|---|
| Exchange (topic) | `mercado.pagamentos` | — | sim | exchange principal |
| Exchange (topic) | `mercado.pagamentos.dlx` | — | sim | dead letter exchange |
| Fila | `pagamento.solicitado` | `pagamento.solicitado` | sim | `x-dead-letter-exchange=mercado.pagamentos.dlx`, `x-dead-letter-routing-key=pagamento.solicitado.dlq` |
| Fila | `pagamento.processado` | `pagamento.processado` | sim | DLX para `pagamento.processado.dlq` |
| Fila (DLQ) | `pagamento.solicitado.dlq` | `pagamento.solicitado.dlq` | sim | solicitações que esgotaram o retry |
| Fila (DLQ) | `pagamento.processado.dlq` | `pagamento.processado.dlq` | sim | resultados que o mercado não conseguiu aplicar |

| Mensagem | Produtor → Consumidor | Campos |
|---|---|---|
| `PagamentoSolicitadoEvent` | mercado-express → pagamento-integracao | `compraId`, `valor`, `clienteNome`, `clienteEmail`, `clienteCpf`, `numeroCartao`, `titularCartao`, `solicitadoEm` |
| `PagamentoProcessadoEvent` | pagamento-integracao → mercado-express | `compraId`, `status` (APROVADO/RECUSADO), `codigoAutorizacao`, `motivo`, `transacaoId`, `processadoEm` |

Todas as mensagens são JSON (conversor Jackson), **persistentes**
(`delivery_mode=2`) e levam **`correlationId` = id da compra**.
No painel: [exchanges (02)](docs/02-rabbitmq-exchanges.png),
[filas (03)](docs/03-rabbitmq-filas.png) e uma
[mensagem com essas propriedades (04)](docs/04-rabbitmq-mensagem-na-fila.png).

---

## Regras do Banco Tranquilo

Regras determinísticas, avaliadas nesta ordem, para demonstrar os dois casos
sem sorte. Todas podem ser trocadas por variável de ambiente
(`banco-tranquilo-api/src/main/resources/application.yml`) e consultadas em
`GET http://localhost:18082/api/pagamentos/regras`.

| # | Condição | Resultado | Motivo devolvido |
|---|---|---|---|
| 1 | valor **> R$ 5.000,00** | RECUSADO | Valor acima do limite de R$ 5.000,00 por transação |
| 2 | cartão **termina em 0000** | RECUSADO | Cartão recusado pelo emissor |
| 3 | CPF em **`111.111.111-11`, `222.222.222-22` ou `123.456.789-09`** | RECUSADO | CPF com restrição cadastral |
| 4 | qualquer outro caso | **APROVADO** | Pagamento aprovado pelo Banco Tranquilo + código `BTnnnnnn` |

Antes de responder, o banco espera um tempo aleatório entre **500 e 1500 ms**,
para a assincronia ficar visível na tela do pedido.

**Dados prontos para a demonstração**

| Cenário | Cartão | CPF | Itens |
|---|---|---|---|
| Aprovada | `4111 1111 1111 1111` | `529.982.247-25` | qualquer total até R$ 5.000 |
| Recusada (cartão) | `5555 4444 3333 0000` | `529.982.247-25` | qualquer |
| Recusada (CPF) | `4111 1111 1111 1111` | `111.111.111-11` | qualquer |
| Recusada (valor) | `4111 1111 1111 1111` | `529.982.247-25` | 14× Kit churrasco (id 14) + 2× Cafeteira (id 16) = R$ 5.338,40 |

---

## Criação do projeto

Os três serviços foram gerados pela **API do Spring Initializr** (nenhum pom de
serviço foi escrito à mão). Comandos usados, a partir da raiz do repositório:

```bash
BASE="https://start.spring.io/starter.zip"
COMMON="type=maven-project&language=java&bootVersion=4.1.1&javaVersion=21&groupId=br.com.fiap&packaging=jar&version=0.0.1-SNAPSHOT"

# mercado-express: vitrine, carrinho, API, consumidor, e-mail e dashboard
curl -fsS "$BASE?$COMMON&artifactId=mercado-express&name=mercado-express&baseDir=mercado-express&packageName=br.com.fiap.mercadoexpress&dependencies=web,thymeleaf,data-jpa,postgresql,h2,rabbitmq,mail,validation,lombok,actuator" -o me.zip

# pagamento-integracao: consumidor RabbitMQ + RestClient
curl -fsS "$BASE?$COMMON&artifactId=pagamento-integracao&name=pagamento-integracao&baseDir=pagamento-integracao&packageName=br.com.fiap.pagamentointegracao&dependencies=web,spring-restclient,rabbitmq,validation,lombok,actuator" -o pi.zip

# banco-tranquilo-api: API mock
curl -fsS "$BASE?$COMMON&artifactId=banco-tranquilo-api&name=banco-tranquilo-api&baseDir=banco-tranquilo-api&packageName=br.com.fiap.bancotranquilo&dependencies=web,validation,lombok,actuator" -o bt.zip

for z in me pi bt; do unzip -q $z.zip && rm $z.zip; done
```

O commit `chore: projetos gerados pelo spring initializr` guarda os três
projetos exatamente como vieram do Initializr. Depois disso, os poms gerados
receberam só três ajustes: a dependência do `mercado-commons`, o H2 restrito
aos testes e a remoção do `spring-boot-h2console`.

Duas peças **não** vêm do Initializr e foram escritas à mão:

- `pom.xml` da raiz: agregador (reactor) que lista os quatro módulos;
- `mercado-commons/pom.xml`: biblioteca simples (não é aplicação Spring Boot).

> **Spring Boot 4.1.1 em vez de 3.x.** O enunciado fala em Spring Boot 3.x, mas
> hoje o Initializr só gera projetos 4.x. A tentativa com 3.5 devolve
> `Invalid Spring Boot version '3.5.7', Spring Boot compatibility range is >=4.0.0`.
> Como a geração pelo Initializr era obrigatória, usamos a 4.1.1, a mesma
> versão da Parte I.

---

## Como rodar

### Tudo em Docker (um comando)

Pré-requisito: Docker Desktop.

```bash
docker compose up --build
```

| O quê | Endereço |
|---|---|
| Loja (vitrine e carrinho) | http://localhost:18080 |
| Dashboard de gerência | http://localhost:18080/gerencia |
| API de compras | http://localhost:18080/api/compras |
| pagamento-integracao (health) | http://localhost:18081/actuator/health |
| Banco Tranquilo | http://localhost:18082/api/pagamentos/regras |
| Painel do RabbitMQ | http://localhost:15672 (`guest` / `guest`) |
| Mailpit (e-mails) | http://localhost:8025 |
| PostgreSQL | `localhost:5434` (`mercado` / `mercado`) |

Os serviços usam **18080, 18081 e 18082**, e não as portas padrão do Spring Boot,
para não colidir com outros projetos que costumam ocupá-las. A porta é a mesma dentro e fora do
container, em todos os `application.yml`, no compose, no Postman e no script.

Para recomeçar do zero (o volume do banco é apagado e o `script.sql` roda de novo):

```bash
docker compose down -v && docker compose up --build
```

**Porta ocupada?** Todas as portas do host podem ser trocadas num `.env` na
raiz (modelo em [`.env.example`](.env.example)), por exemplo `MERCADO_PORT=28080`.

### Pelo IntelliJ (serviços fora do Docker)

```bash
docker compose up -d postgres rabbitmq mailpit
./mvnw clean install          # compila tudo e instala o mercado-commons
```

Depois rode as classes `BancoTranquiloApiApplication`, `PagamentoIntegracaoApplication`
e `MercadoExpressApplication`. Os `application.yml` já apontam para `localhost`.

---

## Endpoints

### mercado-express (18080)

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/compras` | Cria a compra: **202 Accepted**, `Location: /api/compras/{id}`, status PENDENTE |
| `GET` | `/api/compras/{id}` | Consulta status, itens, código de autorização, motivo e e-mails enviados |
| `GET` | `/api/produtos` | Catálogo resumido (para descobrir o `produtoId`) |
| `GET` | `/` | Vitrine (filtro por setor) |
| `GET` | `/carrinho` | Carrinho + formulário de finalização |
| `POST` | `/carrinho/adicionar` · `/carrinho/alterar` · `/carrinho/remover` · `/carrinho/finalizar` | Ações do carrinho |
| `GET` | `/pedidos/{id}` | Acompanhamento do pedido em tempo real |
| `GET` | `/gerencia?dias=7\|15\|30` | Dashboard de gerência |

Erros da API seguem o formato ProblemDetail: **400** (validação, com
`campos`), **422** (produto inexistente ou sem estoque) e **404**.

### banco-tranquilo-api (18082)

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/pagamentos` | Autoriza ou recusa (`compraId`, `valor`, `cpf`, `numeroCartao`, `titular`) |
| `GET` | `/api/pagamentos/regras` | Regras em vigor |

### Postman

Importe [`postman/MercadoExpress.postman_collection.json`](postman/MercadoExpress.postman_collection.json).
Ela traz: listar produtos, **criar compra aprovada**, **consultar status**, **criar
compra recusada** (cartão, CPF e valor), validação 400 e **chamar o banco
direto** (aprovado, recusado e regras). Cada POST de compra guarda o id em
`{{compraId}}` para a consulta seguinte.

Prints: [compra aprovada (11)](docs/11-postman-compra-aprovada.png),
[status aprovada (12)](docs/12-postman-status-aprovada.png),
[compra recusada (13)](docs/13-postman-compra-recusada.png),
[status recusada (14)](docs/14-postman-status-recusada.png) e
[banco direto (15)](docs/15-postman-banco-direto.png).

---

## JSON de exemplo

`POST http://localhost:18080/api/compras`

```json
{
  "cliente": {
    "nome": "Ana Souza",
    "email": "ana.souza@exemplo.com",
    "cpf": "529.982.247-25"
  },
  "pagamento": {
    "numeroCartao": "4111 1111 1111 1111",
    "titular": "ANA SOUZA"
  },
  "itens": [
    { "produtoId": 1, "quantidade": 3 },
    { "produtoId": 6, "quantidade": 2 }
  ]
}
```

Resposta imediata, **202 Accepted**:

```json
{
  "id": 43,
  "status": "PENDENTE",
  "descricaoStatus": "Aguardando pagamento",
  "origem": "API",
  "clienteNome": "Ana Souza",
  "clienteEmail": "ana.souza@exemplo.com",
  "cartaoFinal": "1111",
  "valorTotal": 67.77,
  "itens": [
    { "produtoId": 1, "produto": "Banana prata", "quantidade": 3, "precoUnitario": 7.99, "subtotal": 23.97 },
    { "produtoId": 6, "produto": "Café torrado e moído", "quantidade": 2, "precoUnitario": 21.90, "subtotal": 43.80 }
  ],
  "codigoAutorizacao": null,
  "motivo": null,
  "emails": [],
  "consulta": "/api/compras/43"
}
```

Cerca de 1–2 s depois, `GET /api/compras/43`:

```json
{
  "id": 43,
  "status": "APROVADO",
  "descricaoStatus": "Pagamento aprovado",
  "codigoAutorizacao": "BT644742",
  "motivo": "Pagamento aprovado pelo Banco Tranquilo",
  "emails": [
    { "destinatario": "ana.souza@exemplo.com", "assunto": "Pedido #43 aprovado | Mercado Express",
      "status": "ENVIADO", "erro": null, "dataEnvio": "2026-10-15T15:37:58.1" }
  ]
}
```

Com `curl`:

```bash
curl -i -X POST http://localhost:18080/api/compras \
  -H "Content-Type: application/json" \
  -d '{"cliente":{"nome":"Ana Souza","email":"ana.souza@exemplo.com","cpf":"529.982.247-25"},"pagamento":{"numeroCartao":"4111 1111 1111 1111","titular":"ANA SOUZA"},"itens":[{"produtoId":1,"quantidade":3}]}'
```

---

## Configuração do Banco de Dados

> O mesmo conteúdo está em [`CONFIG_BD.txt`](CONFIG_BD.txt), na raiz.

| Item | Valor |
|---|---|
| SGBD | PostgreSQL 16 (imagem `postgres:16` do docker compose) |
| Host | `localhost` (de dentro do compose: `postgres`) |
| Porta | `5434` no host → `5432` no container |
| Database | `mercadoexpress` |
| Usuário | `mercado` |
| Senha | `mercado` |
| JDBC | `jdbc:postgresql://localhost:5434/mercadoexpress` |
| Script | [`database/script.sql`](database/script.sql) |

O `script.sql` roda **sozinho** na primeira subida do container (montado em
`/docker-entrypoint-initdb.d`). Para rodar manualmente:

```bash
psql -h localhost -p 5434 -U mercado -d mercadoexpress -f database/script.sql
```

Ele é idempotente (faz `DROP` antes) e cria:

| Tabela | Conteúdo |
|---|---|
| `TDS_PRODUTOS_MERCADO` | Produto da Parte I + `PRECO_VENDA`, `PRECO_CUSTO` e `ESTOQUE_MINIMO` · 16 produtos |
| `TDS_COMPRAS_MERCADO` | Compra, status, cartão (só o final), totais de venda e custo · 42 compras históricas (1 PENDENTE) |
| `TDS_ITENS_COMPRA_MERCADO` | Itens com preço e custo congelados na compra · 98 itens |
| `TDS_EMAILS_MERCADO` | Controle de e-mails (destinatário, assunto, compra, status, erro, data) · 41 registros |

As 42 compras históricas (33 aprovadas, 8 recusadas pelas três regras do
banco e a **#42 PENDENTE**, reservada para o
[teste pelo painel do RabbitMQ](#teste-pelo-painel-do-rabbitmq)) usam datas relativas (`CURRENT_DATE - n`). Assim o dashboard mostra
os últimos 30 dias preenchidos em qualquer dia em que o script rodar. Três
produtos ficam abaixo do estoque mínimo de propósito, para o alerta aparecer.

A aplicação usa `ddl-auto: validate`: quem cria o schema é o script, e o
Hibernate só confere o mapeamento. As credenciais chegam por variável de
ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`).

---

## Dashboard de gerência

`http://localhost:18080/gerencia` (filtro de 7, 15 ou 30 dias), com Chart.js:

- **KPIs:** faturamento, lucro bruto e margem, ticket médio, pedidos aprovados
  (com taxa de aprovação) e recusados (com pendentes no momento);
- **Vendas por dia:** barras empilhadas de aprovados e recusados;
- **Faturamento x lucro:** duas linhas no mesmo eixo em R$;
- **Top produtos:** receita dos 8 mais vendidos;
- **Status dos pagamentos:** rosca com aprovado, recusado e pendente;
- **Estoque por produto:** barras com o **alerta de estoque baixo** em vermelho,
  mais os cartões de aviso;
- **Últimos e-mails** (de `TDS_EMAILS_MERCADO`) e **compras recentes**.

Lucro = `VALOR_TOTAL − CUSTO_TOTAL` das compras aprovadas. Por isso o
produto ganhou `PRECO_CUSTO`, que é copiado para o item no momento da compra.
As cores foram checadas para daltonismo: verde × vermelho reprovou no
validador, então "recusado" usa laranja e o vermelho fica só para o alerta de
estoque, sempre com ícone e texto.

Prints: [indicadores e séries (19)](docs/19-dashboard-indicadores.png) e
[produtos, pagamentos e estoque (20)](docs/20-dashboard-graficos.png).

---

## Controle de e-mails

- Enviados pelo `JavaMailSender` com template HTML Thymeleaf
  (`templates/email/resultado-compra.html`): itens, total, cartão, código de
  autorização ou motivo da recusa e um link para o pedido.
- **Toda** tentativa vira uma linha em `TDS_EMAILS_MERCADO`, com status
  **ENVIADO** ou **FALHA** e a mensagem do SMTP em `ERRO`.
- Falha de SMTP **não derruba o fluxo**. O status da compra é gravado numa
  transação anterior; a exceção do envio é capturada e registrada como FALHA,
  e a mensagem do RabbitMQ é confirmada normalmente.

```sql
SELECT ID, DESTINATARIO, ASSUNTO, COMPRA_ID, STATUS, ERRO, DATA_ENVIO
  FROM TDS_EMAILS_MERCADO ORDER BY DATA_ENVIO DESC;
```

Prints: [e-mail aprovado (16)](docs/16-email-mailpit-aprovado.png),
[e-mail recusado (17)](docs/17-email-mailpit-recusado.png) e
[tabela no PostgreSQL (18)](docs/18-tabela-emails-postgres.png).

### Trocar o Mailpit por Gmail (e-mail real)

1. Ative a verificação em duas etapas na conta Google.
2. Gere uma **senha de app** em <https://myaccount.google.com/apppasswords>.
3. Crie um `.env` na raiz (o arquivo fica fora do Git):

   ```env
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=sua.conta@gmail.com
   MAIL_PASSWORD=suasenhadeapp16dig
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM=Mercado Express <sua.conta@gmail.com>
   ```

4. `docker compose up -d mercado-express` e faça uma compra com o seu e-mail.

O remetente (`MAIL_FROM`) precisa ser a própria conta autenticada. Se a senha
estiver errada, a compra continua normal e a tabela registra `FALHA` com o erro
de autenticação.

---

## Resiliência: retry, DLQ e idempotência

| Situação | Comportamento |
|---|---|
| Banco Tranquilo fora do ar | `pagamento-integracao` repete a chamada com **backoff exponencial**: 1 tentativa + 4 retentativas (espera de 2 s, 4 s, 8 s e 10 s) |
| Retry esgotado | `DlqLogRecoverer` registra o motivo e rejeita sem requeue; o RabbitMQ manda pela **DLX** para **`pagamento.solicitado.dlq`**, e a compra continua PENDENTE |
| Mesma resposta entregue duas vezes | **Idempotência**: o mercado trava a compra (`SELECT ... FOR UPDATE`) e ignora o resultado se ela já não estiver PENDENTE |
| Banco de dados do mercado fora ao aplicar o resultado | 3 retentativas e depois `pagamento.processado.dlq` (nada se perde) |
| Publicação × commit | `pagamento.solicitado` só é publicado **depois do commit** da compra |
| Estoque | baixa atômica (`UPDATE ... WHERE ESTOQUE >= :qtd`), só na aprovação |

**Demonstrar a DLQ:**

```bash
docker compose stop banco-tranquilo-api
# faça uma compra (Postman, curl ou loja) e acompanhe:
docker compose logs -f pagamento-integracao
# ~25 s depois: painel RabbitMQ → Queues → pagamento.solicitado.dlq (1 mensagem)
docker compose start banco-tranquilo-api
```

Resultado no painel: [print 22](docs/22-rabbitmq-dlq.png), mensagem na DLQ com
`x-death` (`reason: rejected`, fila de origem `pagamento.solicitado`).

**Ver uma mensagem parada na fila:** pare o consumidor com
`docker compose stop pagamento-integracao`, faça uma compra e use *Get
messages* em `pagamento.solicitado` com *Ack mode: Nack message requeue true*
([print 04](docs/04-rabbitmq-mensagem-na-fila.png)). Depois,
`docker compose start pagamento-integracao` processa o que ficou na fila.

Para reprocessar, abra a mensagem no painel (Queues → `pagamento.solicitado.dlq`
→ *Get messages*), copie o payload e publique de novo como em
[Teste pelo painel do RabbitMQ](#teste-pelo-painel-do-rabbitmq).

---

## Teste pelo painel do RabbitMQ

Teste interno pedido no enunciado: uma solicitação de pagamento publicada **à
mão** no painel do RabbitMQ, sem passar pelo mercado-express. O
`pagamento-integracao` consome, chama o banco e publica o resultado, e o
mercado atualiza a compra.

O `database/script.sql` já deixa a **compra #42 como PENDENTE** (João Pedro
Martins, 3× Sabão em pó = R$ 74,70, sem e-mail enviado) só para este teste.

1. Abra http://localhost:15672 (`guest` / `guest`) → **Exchanges** →
   **`mercado.pagamentos`** → seção **Publish message**.
2. Preencha:

| Campo da tela | Valor |
|---|---|
| Routing key | `pagamento.solicitado` |
| Headers | *(nenhum; o `__TypeId__` não é necessário)* |
| Properties | `content_type` = `application/json` *(obrigatória)* |
| Properties *(opcional)* | `delivery_mode` = `2`, para a mensagem ser persistente |
| Payload | o JSON abaixo |

```json
{
  "compraId": 42,
  "valor": 74.70,
  "clienteNome": "João Pedro Martins",
  "clienteEmail": "joao.martins@exemplo.com",
  "clienteCpf": "389.185.936-86",
  "numeroCartao": "4111111111111111",
  "titularCartao": "JOAO PEDRO MARTINS"
}
```

3. Clique em **Publish message**. O painel mostra *Message published*.

**Resultado esperado (1–2 s depois)**

| Onde olhar | O que aparece |
|---|---|
| `docker compose logs pagamento-integracao` | `Compra 42: solicitando R$ 74.70 ao Banco Tranquilo` e `Compra 42: APROVADO publicado em pagamento.processado` |
| `docker compose logs mercado-express` | `Compra 42: resultado APROVADO recebido (correlationId=42)` |
| http://localhost:18080/api/compras/42 | `"status": "APROVADO"`, `codigoAutorizacao` `BTnnnnnn` e o e-mail `ENVIADO` |
| http://localhost:18080/pedidos/42 | linha do tempo completa |
| http://localhost:8025 | e-mail "Pedido #42 aprovado" |
| Estoque do Sabão em pó (id 11) | 48 → 45 |

**Variações**

- **Recusa pelo painel:** use o `compraId` e o `valor` de uma compra que ficou
  PENDENTE (por exemplo a do teste da DLQ) e troque `numeroCartao` para
  `5555444433330000`. O resultado é RECUSADO com "Cartão recusado pelo emissor".
- **Idempotência:** publique o mesmo payload da #42 de novo. O integração
  chama o banco outra vez, mas o mercado registra
  `Compra 42: ja esta APROVADO, resultado ... ignorado (idempotencia)`: o
  status não muda, o estoque não baixa de novo e nenhum e-mail sai.

**Por que funciona sem `__TypeId__`:** o conversor JSON
([`RabbitTopologiaConfig`](mercado-commons/src/main/java/br/com/fiap/mercadocommons/mensageria/RabbitTopologiaConfig.java))
usa `alwaysConvertToInferredType`, ou seja, o tipo vem do parâmetro do
`@RabbitListener` (`PagamentoSolicitadoEvent`), e não do header. Basta o
`content_type=application/json`; sem ele, o corpo chega como bytes e o
listener rejeita a mensagem.

![Publish manual no painel do RabbitMQ](docs/21-rabbitmq-publish-manual.png)

---

## Testes

```bash
./mvnw clean package                  # 4 módulos, 26 testes automatizados
./scripts/teste-fluxo.sh              # ponta a ponta via curl com o compose no ar
./scripts/teste-fluxo.sh --dlq        # + derruba o banco e mostra retry -> DLQ
```

| Módulo | O que os testes cobrem |
|---|---|
| `banco-tranquilo-api` | As três regras de recusa, o limite exato, a aprovação e os endpoints (200 e 400) |
| `pagamento-integracao` | Publicação do resultado, repasse da falha do banco para o retry e mensagem do painel sem `__TypeId__` (ou com um errado) |
| `mercado-express` | Compra PENDENTE + publicação após o commit com correlationId persistente, aprovação baixando estoque, **idempotência**, recusa sem baixa, **SMTP fora gerando FALHA sem desfazer a compra**, API 202/400/404 e telas (carrinho → finalizar → pedido, `/gerencia`) |

Os testes do mercado usam H2 em memória (perfil `test`) e um RabbitTemplate
simulado, então rodam sem Docker.

---

## Estrutura do repositório

```
mercadoexpress-pagamentos/
├── pom.xml                      # agregador (reactor) dos 4 módulos
├── docker-compose.yml           # postgres, rabbitmq, mailpit + 3 serviços
├── .env.example                 # portas e SMTP (Gmail) configuráveis
├── CONFIG_BD.txt                # configuração do banco de dados
├── integrantes.txt
├── database/script.sql          # DDL + massa de dados
├── postman/MercadoExpress.postman_collection.json
├── scripts/teste-fluxo.sh
├── docs/                        # prints 01-23 (seção Prints)
├── mercado-commons/             # eventos, RabbitNomes, RabbitTopologiaConfig
├── banco-tranquilo-api/         # API mock (Initializr)
├── pagamento-integracao/        # consumidor + RestClient + retry/DLQ (Initializr)
└── mercado-express/             # loja, API, consumidor, e-mail, dashboard (Initializr)
    └── src/main/resources/
        ├── templates/{vitrine,carrinho,pedido,gerencia,email,fragments}
        └── static/{css/app.css,js,img}   # identidade visual da Parte I
```

---

## Prints

Capturas de uma execução real com `docker compose up --build` (16/10/2026), na
ordem da demonstração. Os números de pedido citados são os que aparecem nas imagens.

### 1. Infraestrutura

**01 · Containers no ar.** `docker compose ps` com os seis serviços: os três
Spring Boot em 18080/18081/18082, mais PostgreSQL (5434), RabbitMQ (5672/15672)
e Mailpit (1025/8025), com healthcheck *healthy*.

![Containers no ar](docs/01-containers-no-ar.png)

**02 · Exchanges.** `mercado.pagamentos` e `mercado.pagamentos.dlx`, ambas
*topic* e duráveis (D), declaradas pelos `Declarables` do `mercado-commons`.

![RabbitMQ - exchanges](docs/02-rabbitmq-exchanges.png)

**03 · Filas.** As quatro filas duráveis: `pagamento.solicitado` e
`pagamento.processado` com DLX/DLK, mais as duas DLQs.

![RabbitMQ - filas](docs/03-rabbitmq-filas.png)

**04 · Mensagem na fila.** Mensagem do pedido #43 parada em
`pagamento.solicitado`, lida com *Get messages* (requeue true): `correlation_id`
= id da compra, `delivery_mode: 2` (persistente), `content_type:
application/json` e o header `__TypeId__` que o RabbitTemplate coloca. Para a
mensagem ficar visível na fila, o consumidor foi parado antes com
`docker compose stop pagamento-integracao`.

![RabbitMQ - mensagem na fila](docs/04-rabbitmq-mensagem-na-fila.png)

### 2. Loja: vitrine, carrinho e pedido

**05 · Vitrine** em http://localhost:18080, com filtro por setor, etiqueta de
preço e estoque de cada produto.

![Vitrine](docs/05-vitrine.png)

**06 · Carrinho** com três itens (R$ 58,39) e o formulário de finalização.

![Carrinho](docs/06-carrinho.png)

**07 · Pedido #43 PENDENTE.** Logo depois de finalizar, a compra já está
gravada e publicada; a tela consulta a API a cada segundo e o cronômetro mostra
a espera pelo banco (consumidor parado, como no print 04).

![Pedido pendente](docs/07-pedido-pendente.png)

**08 · Pedido #43 RECUSADO.** Com o consumidor religado, o Banco Tranquilo
recusou pela regra do CPF bloqueado (`111.111.111-11`): "CPF com restrição
cadastral". A linha do tempo fecha com o e-mail enviado.

![Pedido recusado](docs/08-pedido-recusado.png)

**09 · Novo carrinho** (R$ 17,75) com o cartão de teste `4111…1111` e um CPF
fora da lista de bloqueio.

![Carrinho - finalização](docs/09-carrinho-checkout.png)

**10 · Pedido #44 APROVADO** com código de autorização `BT087591`, estoque
baixado e e-mail enviado.

![Pedido aprovado](docs/10-pedido-aprovado.png)

### 3. API REST no Postman

**11 · Criar compra aprovada.** `POST {{mercadoUrl}}/api/compras` responde
**202 Accepted** com a compra #45 em `PENDENTE` (testes do Postman 2/2).

![Postman - compra aprovada](docs/11-postman-compra-aprovada.png)

**12 · Consultar status.** `GET /api/compras/45` já devolve `APROVADO`: o
resultado veio pelo RabbitMQ entre as duas chamadas.

![Postman - status aprovada](docs/12-postman-status-aprovada.png)

**13 · Criar compra recusada.** Cartão `5555 4444 3333 0000`: 202 com a #46 em
`PENDENTE`.

![Postman - compra recusada](docs/13-postman-compra-recusada.png)

**14 · Status da recusada.** `GET /api/compras/46` mostra `RECUSADO`, motivo
"Cartão recusado pelo emissor" e `codigoAutorizacao: null`.

![Postman - status recusada](docs/14-postman-status-recusada.png)

**15 · Banco Tranquilo direto.** `POST {{bancoUrl}}/api/pagamentos`, sem
RabbitMQ: resposta síncrona `APROVADO` (975 ms, por causa do atraso simulado).

![Postman - banco direto](docs/15-postman-banco-direto.png)

### 4. E-mails

**16 · E-mail de aprovação no Mailpit** (pedido #44): template HTML com a
identidade da loja, itens, cartão e autorização.

![E-mail aprovado](docs/16-email-mailpit-aprovado.png)

**17 · E-mail de recusa** (pedido #43) com o motivo informado pelo banco.

![E-mail recusado](docs/17-email-mailpit-recusado.png)

**18 · Controle de e-mails no PostgreSQL.** `SELECT * FROM tds_emails_mercado`:
destinatário, assunto, `compra_id`, status `ENVIADO` e data de cada envio,
incluindo os pedidos #43 a #46 acima.

![Tabela de e-mails](docs/18-tabela-emails-postgres.png)

### 5. Dashboard de gerência

**19 · Indicadores e séries.** KPIs dos últimos 30 dias, vendas por dia
(aprovados × recusados) e faturamento × lucro.

![Dashboard - indicadores](docs/19-dashboard-indicadores.png)

**20 · Produtos, pagamentos e estoque.** Top produtos, status dos pagamentos,
estoque com o alerta dos três itens abaixo do mínimo, últimos e-mails e
compras recentes.

![Dashboard - gráficos](docs/20-dashboard-graficos.png)

### 6. Testes internos de mensageria

**21 · Publish manual no painel do RabbitMQ.** Solicitação da compra #42
publicada à mão na exchange `mercado.pagamentos`, só com `content_type`, sem
headers. O painel confirma *Message published*. Passo a passo em
[Teste pelo painel do RabbitMQ](#teste-pelo-painel-do-rabbitmq).

![Publish manual](docs/21-rabbitmq-publish-manual.png)

**22 · DLQ.** Com o `banco-tranquilo-api` parado, a compra #47 esgotou as
tentativas e caiu em `pagamento.solicitado.dlq` pela `mercado.pagamentos.dlx`.
Os headers `x-death` mostram `reason: rejected` e a fila de origem.

![DLQ](docs/22-rabbitmq-dlq.png)

**23 · Script de teste.** `bash scripts/teste-fluxo.sh`: aprovada (#48), as três
recusas (#49 cartão, #50 CPF, #51 valor), banco direto e validação 400.

![Script de teste](docs/23-script-teste-fluxo.png)

---

## Decisões técnicas

- **Spring Boot 4.1.1** em vez de 3.x: o Initializr, que era de uso obrigatório,
  não gera mais 3.x (veja [Criação do projeto](#criação-do-projeto)).
- **`JacksonJsonMessageConverter`**: no Spring AMQP 4 (que vem com o Boot 4), o
  `Jackson2JsonMessageConverter` foi substituído por este conversor, baseado no
  Jackson 3. O formato na fila é o mesmo (JSON + header `__TypeId__`), e o
  conversor só desserializa classes do pacote de eventos do `mercado-commons`.
- **Portas 18080/18081/18082:** o enunciado não fixa portas; as padrão do Spring Boot
  costumam estar ocupadas por outros projetos na máquina de desenvolvimento.
- **Tipo inferido pelo listener** (`alwaysConvertToInferredType`): a mensagem
  publicada à mão no painel do RabbitMQ funciona só com `content_type`.
- **Publicação após o commit** (`@TransactionalEventListener(AFTER_COMMIT)`):
  evita que o resultado chegue antes de a compra existir no banco.
- **Uma DLQ a mais** (`pagamento.processado.dlq`): o enunciado pede a DLQ das
  solicitações; a dos resultados garante que uma resposta do banco não se perca
  se o PostgreSQL do mercado cair.
- **Cartão**: o número completo só passa pela mensagem até o banco mock; no
  mercado fica gravado apenas o final (`CARTAO_FINAL`). Num gateway real a
  mensagem levaria um token.
- **Uma regra para loja e API**: o carrinho Thymeleaf monta o mesmo
  `CompraRequest` da API e chama o mesmo `CompraService`.

---

## Integrantes

| Nome | RM |
|---|---|
| Olavo Porto Neves | 563558 |
| Pedro Henrique França | 561940 |
| Luiz Gonçalves | 564495 |

Turma 2TDSR · Análise e Desenvolvimento de Sistemas · FIAP
