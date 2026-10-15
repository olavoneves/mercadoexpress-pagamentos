package br.com.fiap.pagamentointegracao.mensageria;

import br.com.fiap.mercadocommons.evento.PagamentoProcessadoEvent;
import br.com.fiap.mercadocommons.evento.PagamentoSolicitadoEvent;
import br.com.fiap.mercadocommons.evento.StatusPagamento;
import br.com.fiap.pagamentointegracao.banco.BancoPagamentoRequest;
import br.com.fiap.pagamentointegracao.banco.BancoPagamentoResponse;
import br.com.fiap.pagamentointegracao.banco.BancoTranquiloClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.EXCHANGE_PAGAMENTOS;
import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.RK_PAGAMENTO_PROCESSADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PagamentoSolicitadoListenerTests {

    private final BancoTranquiloClient banco = mock(BancoTranquiloClient.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PagamentoSolicitadoListener listener = new PagamentoSolicitadoListener(banco, rabbit);

    private final PagamentoSolicitadoEvent evento = PagamentoSolicitadoEvent.builder()
            .compraId(42L).valor(new BigDecimal("99.90")).clienteCpf("52998224725")
            .numeroCartao("4111111111111111").titularCartao("Ana Souza").build();

    @Test
    void publicaRespostaDoBancoEmPagamentoProcessado() {
        when(banco.autorizar(any(BancoPagamentoRequest.class))).thenReturn(BancoPagamentoResponse.builder()
                .compraId(42L).status(StatusPagamento.APROVADO).codigoAutorizacao("BT123456")
                .motivo("ok").transacaoId("t-1").build());

        listener.processar(evento);

        ArgumentCaptor<Object> enviado = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(EXCHANGE_PAGAMENTOS), eq(RK_PAGAMENTO_PROCESSADO), enviado.capture(),
                any(MessagePostProcessor.class));
        PagamentoProcessadoEvent processado = (PagamentoProcessadoEvent) enviado.getValue();
        assertThat(processado.getCompraId()).isEqualTo(42L);
        assertThat(processado.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(processado.getCodigoAutorizacao()).isEqualTo("BT123456");
    }

    @Test
    void relancaFalhaDoBancoParaORetryAgir() {
        when(banco.autorizar(any(BancoPagamentoRequest.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> listener.processar(evento)).isInstanceOf(ResourceAccessException.class);
        verifyNoInteractions(rabbit);
    }
}
