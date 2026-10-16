package br.com.fiap.pagamentointegracao.mensageria;

import br.com.fiap.mercadocommons.evento.PagamentoSolicitadoEvent;
import br.com.fiap.mercadocommons.mensageria.RabbitTopologiaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mensagem publicada a mao pelo painel do RabbitMQ (Publish message): so
 * content_type=application/json, sem o header __TypeId__ que o RabbitTemplate
 * coloca. O conversor deve usar o tipo do parametro do @RabbitListener.
 */
class MensagemDoPainelTests {

    private static final String PAYLOAD = """
            {"compraId": 42, "valor": 74.70, "clienteNome": "João Pedro Martins",
             "clienteEmail": "joao.martins@exemplo.com", "clienteCpf": "389.185.936-86",
             "numeroCartao": "4111111111111111", "titularCartao": "JOAO PEDRO MARTINS"}
            """;

    private final MessageConverter conversor = new RabbitTopologiaConfig().conversorJson();

    private Message mensagem(String typeId) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType("application/json");
        if (typeId != null) {
            propriedades.setHeader("__TypeId__", typeId);
        }
        // o que o MessagingMessageListenerAdapter informa a partir da assinatura do listener
        propriedades.setInferredArgumentType(PagamentoSolicitadoEvent.class);
        return new Message(PAYLOAD.getBytes(StandardCharsets.UTF_8), propriedades);
    }

    @Test
    void desserializaSemTypeId() {
        Object convertido = conversor.fromMessage(mensagem(null));

        assertThat(convertido).isInstanceOf(PagamentoSolicitadoEvent.class);
        PagamentoSolicitadoEvent evento = (PagamentoSolicitadoEvent) convertido;
        assertThat(evento.getCompraId()).isEqualTo(42L);
        assertThat(evento.getValor()).isEqualByComparingTo("74.70");
        assertThat(evento.getClienteNome()).isEqualTo("João Pedro Martins");
    }

    @Test
    void ignoraTypeIdDigitadoErradoNoPainel() {
        Object convertido = conversor.fromMessage(mensagem("java.util.LinkedHashMap"));

        assertThat(convertido).isInstanceOf(PagamentoSolicitadoEvent.class);
    }
}
