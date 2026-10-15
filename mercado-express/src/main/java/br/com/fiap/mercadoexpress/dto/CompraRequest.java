package br.com.fiap.mercadoexpress.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * Corpo de POST /api/compras. Tambem e o formato interno que o carrinho da
 * loja monta antes de chamar o mesmo servico.
 *
 * <pre>
 * {
 *   "cliente":   {"nome": "Ana Souza", "email": "ana@exemplo.com", "cpf": "529.982.247-25"},
 *   "pagamento": {"numeroCartao": "4111 1111 1111 1111", "titular": "ANA SOUZA"},
 *   "itens":     [{"produtoId": 1, "quantidade": 2}]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompraRequest {

    @NotNull(message = "Informe os dados do cliente")
    @Valid
    private Cliente cliente;

    @NotNull(message = "Informe os dados do pagamento")
    @Valid
    private Pagamento pagamento;

    @NotEmpty(message = "A compra precisa de pelo menos um item")
    private List<@Valid Item> itens;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cliente {

        @NotBlank(message = "Informe o nome")
        @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres")
        private String nome;

        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 120, message = "O e-mail pode ter no máximo 120 caracteres")
        private String email;

        @NotBlank(message = "Informe o CPF")
        @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter 11 dígitos")
        private String cpf;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagamento {

        @NotBlank(message = "Informe o número do cartão")
        @Pattern(regexp = "[\\d ]{13,23}", message = "O cartão deve ter de 13 a 19 dígitos")
        @ToString.Exclude
        private String numeroCartao;

        @NotBlank(message = "Informe o nome impresso no cartão")
        @Size(max = 100, message = "O titular pode ter no máximo 100 caracteres")
        private String titular;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotNull(message = "Informe o produtoId")
        private Long produtoId;

        @NotNull(message = "Informe a quantidade")
        @Min(value = 1, message = "A quantidade mínima é 1")
        @Max(value = 99, message = "A quantidade máxima por item é 99")
        private Integer quantidade;
    }
}
