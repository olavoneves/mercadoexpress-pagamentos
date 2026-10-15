package br.com.fiap.mercadoexpress.repository;

import br.com.fiap.mercadoexpress.model.Compra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    /** Compra com itens e produtos carregados, para API, tela do pedido e e-mail. */
    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> buscarCompleta(@Param("id") Long id);

    /**
     * Trava a linha da compra (SELECT ... FOR UPDATE) enquanto o resultado do
     * pagamento e aplicado: se a mesma mensagem chegar duas vezes ao mesmo
     * tempo, a segunda espera e depois encontra a compra ja fora de PENDENTE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> travarParaAtualizacao(@Param("id") Long id);
}
