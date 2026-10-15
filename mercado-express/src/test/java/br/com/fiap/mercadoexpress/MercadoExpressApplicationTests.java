package br.com.fiap.mercadoexpress;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MercadoExpressApplicationTests {

	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	@Test
	void contextLoads() {
	}

}
