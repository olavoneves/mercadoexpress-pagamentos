package br.com.fiap.pagamentointegracao.banco;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/** Contrato de entrada do POST /api/pagamentos do Banco Tranquilo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BancoPagamentoRequest {

    private Long compraId;
    private BigDecimal valor;
    private String cpf;

    @ToString.Exclude
    private String numeroCartao;

    private String titular;
}
