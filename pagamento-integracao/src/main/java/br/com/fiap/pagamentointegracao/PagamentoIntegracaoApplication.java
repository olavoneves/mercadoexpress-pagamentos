package br.com.fiap.pagamentointegracao;

import br.com.fiap.mercadocommons.mensageria.RabbitTopologiaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RabbitTopologiaConfig.class)
public class PagamentoIntegracaoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PagamentoIntegracaoApplication.class, args);
	}

}
