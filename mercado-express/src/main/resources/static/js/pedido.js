/*
 * Acompanhamento do pedido. Enquanto a compra estiver PENDENTE, consulta
 * GET /api/compras/{id} a cada segundo; quando o Banco Tranquilo responde
 * (e o e-mail foi registrado, ou passaram alguns segundos), recarrega a
 * pagina para mostrar o resultado renderizado pelo servidor.
 *
 * Melhoria progressiva: sem JavaScript, basta recarregar a pagina.
 */
(function () {
    var painel = document.querySelector('[data-compra-id]');
    if (!painel) {
        return;
    }
    var id = painel.dataset.compraId;
    var aguardandoEmail = painel.dataset.status !== 'PENDENTE' && painel.dataset.emails === '0';
    if (painel.dataset.status !== 'PENDENTE' && !aguardandoEmail) {
        return;
    }

    var cronometro = painel.querySelector('[data-cronometro]');
    var inicio = Date.now();
    var finalEm = null;

    function consultar() {
        if (cronometro) {
            cronometro.textContent = Math.round((Date.now() - inicio) / 1000) + 's';
        }
        fetch('/api/compras/' + id, {headers: {'Accept': 'application/json'}})
            .then(function (resposta) { return resposta.json(); })
            .then(function (compra) {
                if (compra.status === 'PENDENTE') {
                    return setTimeout(consultar, 1000);
                }
                finalEm = finalEm || Date.now();
                // espera o e-mail ser registrado (no maximo ~6s) para a linha do tempo sair completa
                if ((compra.emails && compra.emails.length > 0) || Date.now() - finalEm > 6000) {
                    return window.location.reload();
                }
                setTimeout(consultar, 1000);
            })
            .catch(function () { setTimeout(consultar, 2000); });
    }

    setTimeout(consultar, 1000);
})();
