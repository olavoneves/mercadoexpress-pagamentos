package br.com.fiap.mercadoexpress.web;

import br.com.fiap.mercadoexpress.dto.CompraRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/** Formulario de finalizacao do carrinho (dados do cliente e do cartao). */
@Data
@NoArgsConstructor
public class CheckoutForm {

    @NotBlank(message = "Informe seu nome")
    @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres")
    private String nome;

    @NotBlank(message = "Informe seu e-mail: é para lá que vai a confirmação")
    @Email(message = "E-mail inválido")
    @Size(max = 120, message = "O e-mail pode ter no máximo 120 caracteres")
    private String email;

    @NotBlank(message = "Informe o CPF")
    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter 11 dígitos")
    private String cpf;

    @NotBlank(message = "Informe o número do cartão")
    @Pattern(regexp = "[\\d ]{13,23}", message = "O cartão deve ter de 13 a 19 dígitos")
    @ToString.Exclude
    private String numeroCartao;

    @NotBlank(message = "Informe o nome impresso no cartão")
    @Size(max = 100, message = "O titular pode ter no máximo 100 caracteres")
    private String titular;

    /** Converte para o mesmo contrato da API: loja e Postman passam pelo mesmo servico. */
    public CompraRequest paraCompra(Map<Long, Integer> itens) {
        return CompraRequest.builder()
                .cliente(new CompraRequest.Cliente(nome, email, cpf))
                .pagamento(new CompraRequest.Pagamento(numeroCartao, titular))
                .itens(itens.entrySet().stream()
                        .map(e -> new CompraRequest.Item(e.getKey(), e.getValue()))
                        .toList())
                .build();
    }

    public static List<String> cartoesDeTeste() {
        return List.of("4111 1111 1111 1111", "5555 4444 3333 0000");
    }
}
