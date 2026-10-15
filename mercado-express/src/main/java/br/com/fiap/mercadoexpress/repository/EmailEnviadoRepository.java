package br.com.fiap.mercadoexpress.repository;

import br.com.fiap.mercadoexpress.model.EmailEnviado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado, Long> {

    List<EmailEnviado> findByCompraIdOrderByDataEnvioDesc(Long compraId);

    List<EmailEnviado> findTop8ByOrderByDataEnvioDescIdDesc();
}
