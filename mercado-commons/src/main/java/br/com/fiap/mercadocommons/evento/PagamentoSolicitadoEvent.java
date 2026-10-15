package br.com.fiap.mercadocommons.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Publicado pelo mercado-express em pagamento.solicitado assim que a compra
 * e gravada como PENDENTE. Consumido pelo pagamento-integracao.
 *
 * <p>O numero do cartao viaja so ate o banco mock e nunca e gravado no
 * mercado (la fica apenas o final). Num gateway real ele seria um token.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoSolicitadoEvent {

    private Long compraId;
    private BigDecimal valor;
    private String clienteNome;
    private String clienteEmail;
    private String clienteCpf;

    @ToString.Exclude
    private String numeroCartao;

    private String titularCartao;
    private LocalDateTime solicitadoEm;
}
