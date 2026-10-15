package br.com.fiap.mercadoexpress.dto;

import br.com.fiap.mercadoexpress.model.Produto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Item de GET /api/produtos: o suficiente para montar uma compra no Postman. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoResponse {

    private Long id;
    private String nome;
    private String setor;
    private String tamanho;
    private BigDecimal precoVenda;
    private Integer estoque;

    public static ProdutoResponse de(Produto produto) {
        return ProdutoResponse.builder()
                .id(produto.getId())
                .nome(produto.getNome())
                .setor(produto.getSetor())
                .tamanho(produto.getTamanho())
                .precoVenda(produto.getPrecoVenda())
                .estoque(produto.getEstoque())
                .build();
    }
}
