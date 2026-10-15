package br.com.fiap.bancotranquilo;

import br.com.fiap.bancotranquilo.config.RegrasBancoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RegrasBancoProperties.class)
public class BancoTranquiloApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BancoTranquiloApiApplication.class, args);
	}

}
