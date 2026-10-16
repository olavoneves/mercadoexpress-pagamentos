package br.com.fiap.mercadocommons.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.*;

/**
 * Topologia RabbitMQ compartilhada. Cada servico que importa esta classe
 * declara exatamente as mesmas exchanges, filas e bindings; o RabbitAdmin do
 * Spring cria o que faltar assim que a conexao abre e, como os argumentos sao
 * identicos, nao importa qual servico sobe primeiro.
 *
 * <pre>
 * mercado.pagamentos (topic)
 *   pagamento.solicitado  -> fila pagamento.solicitado  --DLX--> pagamento.solicitado.dlq
 *   pagamento.processado  -> fila pagamento.processado  --DLX--> pagamento.processado.dlq
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class RabbitTopologiaConfig {

    /** Pacote dos eventos: o conversor so desserializa classes daqui. */
    private static final String PACOTE_EVENTOS = "br.com.fiap.mercadocommons.evento";

    @Bean
    public Declarables topologiaPagamentos() {
        TopicExchange pagamentos = new TopicExchange(EXCHANGE_PAGAMENTOS, true, false);
        TopicExchange dlx = new TopicExchange(EXCHANGE_PAGAMENTOS_DLX, true, false);

        Queue solicitado = QueueBuilder.durable(FILA_PAGAMENTO_SOLICITADO)
                .deadLetterExchange(EXCHANGE_PAGAMENTOS_DLX)
                .deadLetterRoutingKey(RK_PAGAMENTO_SOLICITADO_DLQ)
                .build();
        Queue processado = QueueBuilder.durable(FILA_PAGAMENTO_PROCESSADO)
                .deadLetterExchange(EXCHANGE_PAGAMENTOS_DLX)
                .deadLetterRoutingKey(RK_PAGAMENTO_PROCESSADO_DLQ)
                .build();
        Queue solicitadoDlq = QueueBuilder.durable(FILA_PAGAMENTO_SOLICITADO_DLQ).build();
        Queue processadoDlq = QueueBuilder.durable(FILA_PAGAMENTO_PROCESSADO_DLQ).build();

        Binding bSolicitado = BindingBuilder.bind(solicitado).to(pagamentos).with(RK_PAGAMENTO_SOLICITADO);
        Binding bProcessado = BindingBuilder.bind(processado).to(pagamentos).with(RK_PAGAMENTO_PROCESSADO);
        Binding bSolicitadoDlq = BindingBuilder.bind(solicitadoDlq).to(dlx).with(RK_PAGAMENTO_SOLICITADO_DLQ);
        Binding bProcessadoDlq = BindingBuilder.bind(processadoDlq).to(dlx).with(RK_PAGAMENTO_PROCESSADO_DLQ);

        return new Declarables(pagamentos, dlx,
                solicitado, processado, solicitadoDlq, processadoDlq,
                bSolicitado, bProcessado, bSolicitadoDlq, bProcessadoDlq);
    }

    /**
     * Mensagens em JSON. O Spring Boot liga este bean sozinho ao RabbitTemplate
     * e as fabricas de @RabbitListener.
     *
     * <p>No Spring AMQP 4 (Spring Boot 4) o Jackson2JsonMessageConverter foi
     * sucedido por este JacksonJsonMessageConverter, que usa o Jackson 3 do
     * Boot. O formato e o mesmo: corpo JSON + header __TypeId__.</p>
     *
     * <p>O tipo de destino vem SEMPRE do parametro do @RabbitListener, nao do
     * header __TypeId__. Assim uma mensagem publicada a mao pelo painel do
     * RabbitMQ (Publish message, so com content_type=application/json) e
     * desserializada igual a uma publicada pelo RabbitTemplate.</p>
     */
    @Bean
    public MessageConverter conversorJson() {
        JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter(PACOTE_EVENTOS);
        conversor.setAlwaysConvertToInferredType(true);
        return conversor;
    }
}
