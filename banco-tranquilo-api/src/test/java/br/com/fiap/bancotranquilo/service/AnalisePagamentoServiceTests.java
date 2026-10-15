package br.com.fiap.bancotranquilo.service;

import br.com.fiap.bancotranquilo.config.RegrasBancoProperties;
import br.com.fiap.bancotranquilo.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.dto.PagamentoResponse;
import br.com.fiap.bancotranquilo.dto.StatusTransacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalisePagamentoServiceTests {

    private AnalisePagamentoService analise;

    @BeforeEach
    void configurar() {
        RegrasBancoProperties regras = new RegrasBancoProperties();
        regras.setCpfsBloqueados(List.of("11111111111"));
        regras.setAtrasoMinimoMs(0);
        regras.setAtrasoMaximoMs(0);
        analise = new AnalisePagamentoService(regras);
    }

    private PagamentoRequest pedido(String valor, String cartao, String cpf) {
        return PagamentoRequest.builder()
                .compraId(1L).valor(new BigDecimal(valor))
                .numeroCartao(cartao).cpf(cpf).titular("Ana Souza")
                .build();
    }

    @Test
    void aprovaPagamentoComum() {
        PagamentoResponse resposta = analise.analisar(pedido("120.50", "4111 1111 1111 1111", "529.982.247-25"));

        assertThat(resposta.getStatus()).isEqualTo(StatusTransacao.APROVADO);
        assertThat(resposta.getCodigoAutorizacao()).matches("BT\\d{6}");
        assertThat(resposta.getCompraId()).isEqualTo(1L);
    }

    @Test
    void aprovaExatamenteNoLimite() {
        assertThat(analise.motivoDeRecusa(pedido("5000.00", "4111111111111111", "52998224725"))).isEmpty();
    }

    @Test
    void recusaValorAcimaDoLimite() {
        PagamentoResponse resposta = analise.analisar(pedido("5000.01", "4111111111111111", "52998224725"));

        assertThat(resposta.getStatus()).isEqualTo(StatusTransacao.RECUSADO);
        assertThat(resposta.getCodigoAutorizacao()).isNull();
        assertThat(resposta.getMotivo()).contains("limite");
    }

    @Test
    void recusaCartaoTerminadoEm0000() {
        assertThat(analise.motivoDeRecusa(pedido("10.00", "4111 1111 1111 0000", "52998224725")))
                .hasValue("Cartao recusado pelo emissor");
    }

    @Test
    void recusaCpfBloqueadoComOuSemMascara() {
        assertThat(analise.motivoDeRecusa(pedido("10.00", "4111111111111111", "111.111.111-11")))
                .hasValue("CPF com restricao cadastral");
        assertThat(analise.motivoDeRecusa(pedido("10.00", "4111111111111111", "11111111111")))
                .hasValue("CPF com restricao cadastral");
    }
}
