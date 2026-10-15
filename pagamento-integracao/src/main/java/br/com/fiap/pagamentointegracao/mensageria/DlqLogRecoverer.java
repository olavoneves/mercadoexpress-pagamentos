package br.com.fiap.pagamentointegracao.mensageria;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.stereotype.Component;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.FILA_PAGAMENTO_SOLICITADO_DLQ;

/**
 * Chamado pelo Spring AMQP quando o retry esgota. Registra o motivo no log e
 * rejeita a mensagem sem requeue: o RabbitMQ a encaminha pela DLX
 * mercado.pagamentos.dlx ate a fila pagamento.solicitado.dlq.
 *
 * <p>O Spring Boot usa automaticamente o bean MessageRecoverer que existir no
 * contexto ao montar o interceptor de retry do listener.</p>
 */
@Slf4j
@Component
public class DlqLogRecoverer implements MessageRecoverer {

    private final RejectAndDontRequeueRecoverer rejeitar = new RejectAndDontRequeueRecoverer();

    @Override
    public void recover(Message mensagem, Throwable causa) {
        log.error("Compra {}: tentativas esgotadas, mensagem enviada para {}. Causa: {}",
                mensagem.getMessageProperties().getCorrelationId(), FILA_PAGAMENTO_SOLICITADO_DLQ,
                causa.getCause() != null ? causa.getCause().getMessage() : causa.getMessage());
        rejeitar.recover(mensagem, causa);
    }
}
