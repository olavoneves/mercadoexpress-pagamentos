package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.CompraRepository;
import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Telas Thymeleaf: vitrine, carrinho, finalizacao, pedido e dashboard. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelasTests {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private CompraRepository compras;
    @Autowired
    private EmailEnviadoRepository emails;

    private Produto cafe;

    @BeforeEach
    void preparar() {
        emails.deleteAll();
        compras.deleteAll();
        produtos.deleteAll();
        cafe = produtos.save(Produto.builder()
                .nome("Café torrado e moído").tipo("Mercearia seca").setor("Mercearia").tamanho("500g")
                .precoVenda(new BigDecimal("21.90")).precoCusto(new BigDecimal("14.30"))
                .estoque(5).estoqueMinimo(10).ativo(true).build());
    }

    @Test
    void compraPeloCarrinhoAteAPaginaDoPedido() throws Exception {
        MockHttpSession sessao = new MockHttpSession();

        mvc.perform(get("/").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Café torrado e moído")));

        mvc.perform(post("/carrinho/adicionar").session(sessao)
                        .param("produtoId", cafe.getId().toString()).param("quantidade", "2"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(post("/carrinho/alterar").session(sessao)
                        .param("produtoId", cafe.getId().toString()).param("quantidade", "3"))
                .andExpect(redirectedUrl("/carrinho"));

        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("65,70")));

        // Formulario invalido volta para o carrinho com erros
        mvc.perform(post("/carrinho/finalizar").session(sessao).param("nome", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "nome", "email", "cpf"));

        String destino = mvc.perform(post("/carrinho/finalizar").session(sessao)
                        .param("nome", "Ana Souza").param("email", "ana@exemplo.com")
                        .param("cpf", "529.982.247-25").param("numeroCartao", "4111 1111 1111 1111")
                        .param("titular", "ANA SOUZA"))
                .andExpect(redirectedUrlPattern("/pedidos/*"))
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(destino).session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aguardando pagamento")));

        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void carrinhoNaoPassaDoEstoque() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(post("/carrinho/adicionar").session(sessao)
                        .param("produtoId", cafe.getId().toString()).param("quantidade", "50"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(content().string(containsString("value=\"5\"")));
    }

    @Test
    void dashboardDeGerenciaAbre() throws Exception {
        mvc.perform(get("/gerencia").param("dias", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estoque por produto")))
                .andExpect(content().string(containsString("Café torrado e moído")));
    }

    @Test
    void pedidoInexistenteDa404() throws Exception {
        mvc.perform(get("/pedidos/987654")).andExpect(status().isNotFound());
    }
}
