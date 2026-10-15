package br.com.fiap.pagamentointegracao.mensageria;

import br.com.fiap.mercadocommons.evento.PagamentoProcessadoEvent;
import br.com.fiap.mercadocommons.evento.PagamentoSolicitadoEvent;
import br.com.fiap.mercadocommons.mensageria.MensagemPersistente;
import br.com.fiap.pagamentointegracao.banco.BancoPagamentoRequest;
import br.com.fiap.pagamentointegracao.banco.BancoPagamentoResponse;
import br.com.fiap.pagamentointegracao.banco.BancoTranquiloClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.EXCHANGE_PAGAMENTOS;
import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.FILA_PAGAMENTO_SOLICITADO;
import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.RK_PAGAMENTO_PROCESSADO;

/**
 * Consome pagamento.solicitado, chama o Banco Tranquilo e publica a resposta
 * em pagamento.processado.
 *
 * <p>Se o banco falhar, a excecao e relancada: o retry stateless configurado
 * no application.yml repete a chamada com backoff exponencial e, esgotadas as
 * tentativas, o {@link DlqLogRecoverer} rejeita a mensagem, que cai na DLQ.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoSolicitadoListener {

    private final BancoTranquiloClient banco;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = FILA_PAGAMENTO_SOLICITADO)
    public void processar(PagamentoSolicitadoEvent evento) {
        log.info("Compra {}: solicitando R$ {} ao Banco Tranquilo", evento.getCompraId(), evento.getValor());

        BancoPagamentoResponse resposta;
        try {
            resposta = banco.autorizar(BancoPagamentoRequest.builder()
                    .compraId(evento.getCompraId())
                    .valor(evento.getValor())
                    .cpf(evento.getClienteCpf())
                    .numeroCartao(evento.getNumeroCartao())
                    .titular(evento.getTitularCartao())
                    .build());
        } catch (RestClientException e) {
            log.warn("Compra {}: Banco Tranquilo indisponivel ({}). Nova tentativa com backoff...",
                    evento.getCompraId(), e.getMessage());
            throw e;
        }

        PagamentoProcessadoEvent processado = PagamentoProcessadoEvent.builder()
                .compraId(evento.getCompraId())
                .status(resposta.getStatus())
                .codigoAutorizacao(resposta.getCodigoAutorizacao())
                .motivo(resposta.getMotivo())
                .transacaoId(resposta.getTransacaoId())
                .processadoEm(resposta.getProcessadoEm() != null ? resposta.getProcessadoEm() : LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(EXCHANGE_PAGAMENTOS, RK_PAGAMENTO_PROCESSADO, processado,
                MensagemPersistente.comCorrelacao(evento.getCompraId()));

        log.info("Compra {}: {} publicado em {}", evento.getCompraId(), processado.getStatus(), RK_PAGAMENTO_PROCESSADO);
    }
}
