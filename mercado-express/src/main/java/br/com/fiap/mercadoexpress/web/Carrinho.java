package br.com.fiap.mercadoexpress.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrinho do visitante, guardado na sessao HTTP: produtoId -> quantidade.
 * Os precos nao ficam aqui; sao sempre lidos do banco na hora de exibir e de
 * fechar a compra, para nunca cobrar um preco desatualizado.
 */
@Component
@SessionScope
public class Carrinho implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<Long, Integer> itens = new LinkedHashMap<>();

    public void adicionar(Long produtoId, int quantidade) {
        itens.merge(produtoId, quantidade, Integer::sum);
    }

    /** Quantidade zero ou negativa remove o item. */
    public void alterar(Long produtoId, int quantidade) {
        if (quantidade <= 0) {
            itens.remove(produtoId);
        } else {
            itens.put(produtoId, quantidade);
        }
    }

    public void remover(Long produtoId) {
        itens.remove(produtoId);
    }

    public void limpar() {
        itens.clear();
    }

    public int quantidadeDe(Long produtoId) {
        return itens.getOrDefault(produtoId, 0);
    }

    public Map<Long, Integer> getItens() {
        return Collections.unmodifiableMap(itens);
    }

    public int getQuantidadeTotal() {
        return itens.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isVazio() {
        return itens.isEmpty();
    }
}
