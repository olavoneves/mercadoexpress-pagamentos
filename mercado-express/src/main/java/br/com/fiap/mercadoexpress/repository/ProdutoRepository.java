package br.com.fiap.mercadoexpress.repository;

import br.com.fiap.mercadoexpress.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByAtivoTrueOrderBySetorAscNomeAsc();

    /**
     * Baixa atomica: so desconta se ainda houver estoque suficiente, entao duas
     * aprovacoes simultaneas nunca deixam o estoque negativo.
     *
     * @return 1 se baixou, 0 se o estoque nao era suficiente
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Produto p set p.estoque = p.estoque - :quantidade "
            + "where p.id = :id and p.estoque >= :quantidade")
    int baixarEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);
}
