package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadocommons.mensageria.RabbitTopologiaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RabbitTopologiaConfig.class)
public class MercadoExpressApplication {

	public static void main(String[] args) {
		SpringApplication.run(MercadoExpressApplication.class, args);
	}

}
