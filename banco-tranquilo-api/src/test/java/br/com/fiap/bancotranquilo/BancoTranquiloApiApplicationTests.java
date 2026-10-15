package br.com.fiap.bancotranquilo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"banco.atraso-minimo-ms=0", "banco.atraso-maximo-ms=0"})
@AutoConfigureMockMvc
class BancoTranquiloApiApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void aprovaPeloEndpoint() throws Exception {
		mvc.perform(post("/api/pagamentos").contentType(MediaType.APPLICATION_JSON).content("""
						{"compraId": 7, "valor": 89.90, "cpf": "529.982.247-25",
						 "numeroCartao": "4111 1111 1111 1111", "titular": "Ana Souza"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APROVADO"))
				.andExpect(jsonPath("$.compraId").value(7));
	}

	@Test
	void recusaPeloEndpoint() throws Exception {
		mvc.perform(post("/api/pagamentos").contentType(MediaType.APPLICATION_JSON).content("""
						{"compraId": 8, "valor": 6200.00, "cpf": "529.982.247-25",
						 "numeroCartao": "4111 1111 1111 1111", "titular": "Ana Souza"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RECUSADO"));
	}

	@Test
	void corpoInvalidoDevolve400() throws Exception {
		mvc.perform(post("/api/pagamentos").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

}
