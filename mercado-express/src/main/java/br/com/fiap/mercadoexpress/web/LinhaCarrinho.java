package br.com.fiap.mercadoexpress.web;

import br.com.fiap.mercadoexpress.model.Produto;
import lombok.Value;

import java.math.BigDecimal;

/** Uma linha da tela do carrinho: produto atual do banco + quantidade da sessao. */
@Value
public class LinhaCarrinho {

    Produto produto;
    int quantidade;

    public BigDecimal getSubtotal() {
        return produto.getPrecoVenda().multiply(BigDecimal.valueOf(quantidade));
    }

    public boolean isAcimaDoEstoque() {
        return quantidade > produto.getEstoque();
    }
}
