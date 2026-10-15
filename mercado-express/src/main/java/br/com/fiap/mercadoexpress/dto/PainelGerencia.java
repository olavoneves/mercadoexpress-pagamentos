package br.com.fiap.mercadoexpress.dto;

import br.com.fiap.mercadoexpress.model.EmailEnviado;
import br.com.fiap.mercadoexpress.model.Produto;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Tudo que a tela /gerencia mostra, ja calculado pelo GerenciaService. */
@Value
@Builder
public class PainelGerencia {

    int dias;
    BigDecimal faturamento;
    BigDecimal lucro;
    BigDecimal margemPercentual;
    BigDecimal ticketMedio;
    long aprovados;
    long recusados;
    long pendentes;
    BigDecimal taxaAprovacao;

    List<ProdutoVendido> topProdutos;
    List<Produto> estoque;
    List<Produto> estoqueBaixo;
    List<EmailEnviado> ultimosEmails;
    List<CompraResumo> comprasRecentes;

    /** Series dos graficos (Chart.js), serializadas em JSON na pagina. */
    Map<String, Object> graficos;

    @Value
    public static class ProdutoVendido {
        String nome;
        long quantidade;
        BigDecimal receita;
        BigDecimal lucro;
    }

    @Value
    public static class CompraResumo {
        Long id;
        String clienteNome;
        String origem;
        long itens;
        BigDecimal valorTotal;
        String status;
        String motivo;
        LocalDateTime dataCriacao;
    }
}
