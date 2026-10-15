package br.com.fiap.pagamentointegracao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Sobe o contexto sem broker: os listeners ficam parados durante o teste. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class PagamentoIntegracaoApplicationTests {

	@Test
	void contextLoads() {
	}

}
