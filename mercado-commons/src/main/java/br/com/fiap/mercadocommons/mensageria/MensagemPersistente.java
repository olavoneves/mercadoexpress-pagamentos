package br.com.fiap.mercadocommons.mensageria;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;

/**
 * Ajusta as propriedades AMQP de toda mensagem publicada pelos servicos:
 * entrega persistente (sobrevive a reinicio do broker) e correlationId igual
 * ao id da compra, para rastrear a mesma compra nas duas filas.
 */
public final class MensagemPersistente {

    private MensagemPersistente() {
    }

    public static MessagePostProcessor comCorrelacao(Long compraId) {
        return mensagem -> {
            mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            mensagem.getMessageProperties().setCorrelationId(String.valueOf(compraId));
            return mensagem;
        };
    }
}
