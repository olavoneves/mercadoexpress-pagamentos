package br.com.fiap.bancotranquilo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/** Corpo de POST /api/pagamentos. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoRequest {

    /** Id da compra no mercado, devolvido na resposta para correlacao. */
    @NotNull(message = "Informe o id da compra")
    private Long compraId;

    @NotNull(message = "Informe o valor")
    @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero")
    private BigDecimal valor;

    @NotBlank(message = "Informe o CPF")
    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter 11 digitos")
    private String cpf;

    @NotBlank(message = "Informe o numero do cartao")
    @Pattern(regexp = "[\\d ]{13,23}", message = "Numero do cartao deve ter de 13 a 19 digitos")
    @ToString.Exclude
    private String numeroCartao;

    @NotBlank(message = "Informe o titular do cartao")
    private String titular;
}
