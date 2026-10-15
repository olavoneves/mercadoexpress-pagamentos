package br.com.fiap.mercadoexpress.dto;

import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.EmailEnviado;
import br.com.fiap.mercadoexpress.model.ItemCompra;
import br.com.fiap.mercadoexpress.model.OrigemCompra;
import br.com.fiap.mercadoexpress.model.StatusCompra;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Representacao da compra devolvida por POST e GET /api/compras. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompraResponse {

    private Long id;
    private StatusCompra status;
    private String descricaoStatus;
    private OrigemCompra origem;
    private String clienteNome;
    private String clienteEmail;
    private String cartaoFinal;
    private BigDecimal valorTotal;
    private List<ItemResponse> itens;
    private String codigoAutorizacao;
    private String motivo;
    private LocalDateTime criadaEm;
    private LocalDateTime atualizadaEm;
    private List<EmailResponse> emails;
    /** Onde consultar o status (a resposta do POST e 202: o resultado vem depois). */
    private String consulta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private Long produtoId;
        private String produto;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailResponse {
        private String destinatario;
        private String assunto;
        private StatusEmail status;
        private String erro;
        private LocalDateTime dataEnvio;
    }

    public static CompraResponse de(Compra compra, List<EmailEnviado> emails) {
        return CompraResponse.builder()
                .id(compra.getId())
                .status(compra.getStatus())
                .descricaoStatus(compra.getStatus().getRotulo())
                .origem(compra.getOrigem())
                .clienteNome(compra.getClienteNome())
                .clienteEmail(compra.getClienteEmail())
                .cartaoFinal(compra.getCartaoFinal())
                .valorTotal(compra.getValorTotal())
                .itens(compra.getItens().stream().map(CompraResponse::item).toList())
                .codigoAutorizacao(compra.getCodigoAutorizacao())
                .motivo(compra.getMotivo())
                .criadaEm(compra.getDataCriacao())
                .atualizadaEm(compra.getDataAtualizacao())
                .emails(emails.stream().map(CompraResponse::email).toList())
                .consulta("/api/compras/" + compra.getId())
                .build();
    }

    private static ItemResponse item(ItemCompra item) {
        return ItemResponse.builder()
                .produtoId(item.getProduto().getId())
                .produto(item.getProduto().getNome())
                .quantidade(item.getQuantidade())
                .precoUnitario(item.getPrecoUnitario())
                .subtotal(item.getSubtotal())
                .build();
    }

    private static EmailResponse email(EmailEnviado email) {
        return EmailResponse.builder()
                .destinatario(email.getDestinatario())
                .assunto(email.getAssunto())
                .status(email.getStatus())
                .erro(email.getErro())
                .dataEnvio(email.getDataEnvio())
                .build();
    }
}
