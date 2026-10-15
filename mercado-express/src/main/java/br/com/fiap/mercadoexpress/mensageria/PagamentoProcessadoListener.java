package br.com.fiap.mercadoexpress.mensageria;

import br.com.fiap.mercadocommons.evento.PagamentoProcessadoEvent;
import br.com.fiap.mercadoexpress.service.CompraService;
import br.com.fiap.mercadoexpress.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.FILA_PAGAMENTO_PROCESSADO;

/**
 * Consome pagamento.processado: atualiza a compra (APROVADO baixa o estoque)
 * e dispara o e-mail. O e-mail sai depois do commit da atualizacao, entao
 * uma falha de SMTP nunca desfaz o status da compra.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoProcessadoListener {

    private final CompraService compraService;
    private final EmailService emailService;

    @RabbitListener(queues = FILA_PAGAMENTO_PROCESSADO)
    public void receber(PagamentoProcessadoEvent resultado,
                        @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
        log.info("Compra {}: resultado {} recebido (correlationId={})",
                resultado.getCompraId(), resultado.getStatus(), correlationId);

        compraService.aplicarResultado(resultado).ifPresent(emailService::enviarResultado);
    }
}
