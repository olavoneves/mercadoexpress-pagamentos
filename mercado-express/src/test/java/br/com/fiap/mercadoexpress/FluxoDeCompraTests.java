package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadocommons.evento.PagamentoProcessadoEvent;
import br.com.fiap.mercadocommons.evento.PagamentoSolicitadoEvent;
import br.com.fiap.mercadocommons.evento.StatusPagamento;
import br.com.fiap.mercadoexpress.dto.CompraRequest;
import br.com.fiap.mercadoexpress.exception.CompraInvalidaException;
import br.com.fiap.mercadoexpress.mensageria.PagamentoProcessadoListener;
import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.EmailEnviado;
import br.com.fiap.mercadoexpress.model.OrigemCompra;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusCompra;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.repository.CompraRepository;
import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.service.CompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.EXCHANGE_PAGAMENTOS;
import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.RK_PAGAMENTO_SOLICITADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo do lado do mercado, sem broker: a publicacao e verificada no
 * RabbitTemplate mockado e a resposta do banco e entregue direto ao listener.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FluxoDeCompraTests {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CompraService compraService;
    @Autowired
    private PagamentoProcessadoListener listener;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private CompraRepository compras;
    @Autowired
    private EmailEnviadoRepository emails;
    @Autowired
    private MockMvc mvc;

    private Produto banana;

    @BeforeEach
    void prepararEstoque() {
        emails.deleteAll();
        compras.deleteAll();
        produtos.deleteAll();
        banana = produtos.save(Produto.builder()
                .nome("Banana prata").tipo("Fruta").setor("Hortifruti").tamanho("1kg")
                .precoVenda(new BigDecimal("7.99")).precoCusto(new BigDecimal("4.20"))
                .estoque(10).estoqueMinimo(3).ativo(true).build());
        clearInvocations(rabbitTemplate);
    }

    private CompraRequest pedido(int quantidade) {
        return CompraRequest.builder()
                .cliente(new CompraRequest.Cliente("Ana Souza", "Ana@Exemplo.com", "529.982.247-25"))
                .pagamento(new CompraRequest.Pagamento("4111 1111 1111 1111", "ANA SOUZA"))
                .itens(List.of(new CompraRequest.Item(banana.getId(), quantidade)))
                .build();
    }

    private PagamentoProcessadoEvent resultado(Long compraId, StatusPagamento status) {
        return PagamentoProcessadoEvent.builder()
                .compraId(compraId).status(status)
                .codigoAutorizacao(status == StatusPagamento.APROVADO ? "BT000123" : null)
                .motivo(status == StatusPagamento.APROVADO ? "Pagamento aprovado" : "Cartão recusado pelo emissor")
                .transacaoId("tx-1").build();
    }

    @Test
    void registraPendenteEPublicaAposOCommitComCorrelationId() {
        Compra compra = compraService.registrar(pedido(2), OrigemCompra.API);

        assertThat(compra.getStatus()).isEqualTo(StatusCompra.PENDENTE);
        assertThat(compra.getValorTotal()).isEqualByComparingTo("15.98");
        assertThat(compra.getCustoTotal()).isEqualByComparingTo("8.40");
        assertThat(compra.getCartaoFinal()).isEqualTo("1111");
        assertThat(compra.getClienteEmail()).isEqualTo("ana@exemplo.com");

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> ajuste = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE_PAGAMENTOS), eq(RK_PAGAMENTO_SOLICITADO),
                evento.capture(), ajuste.capture());

        PagamentoSolicitadoEvent solicitado = (PagamentoSolicitadoEvent) evento.getValue();
        assertThat(solicitado.getCompraId()).isEqualTo(compra.getId());
        assertThat(solicitado.getValor()).isEqualByComparingTo("15.98");
        assertThat(solicitado.getNumeroCartao()).isEqualTo("4111111111111111");

        Message mensagem = ajuste.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(mensagem.getMessageProperties().getCorrelationId()).isEqualTo(String.valueOf(compra.getId()));
        assertThat(mensagem.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void aprovadoBaixaEstoqueERespostaRepetidaEIgnorada() {
        Compra compra = compraService.registrar(pedido(3), OrigemCompra.LOJA);

        assertThat(compraService.aplicarResultado(resultado(compra.getId(), StatusPagamento.APROVADO))).isPresent();
        assertThat(produtos.findById(banana.getId()).orElseThrow().getEstoque()).isEqualTo(7);

        // Mesma compra de novo (reentrega ou duplicata): idempotente
        assertThat(compraService.aplicarResultado(resultado(compra.getId(), StatusPagamento.RECUSADO))).isEmpty();
        Compra depois = compraService.buscar(compra.getId());
        assertThat(depois.getStatus()).isEqualTo(StatusCompra.APROVADO);
        assertThat(depois.getCodigoAutorizacao()).isEqualTo("BT000123");
        assertThat(produtos.findById(banana.getId()).orElseThrow().getEstoque()).isEqualTo(7);
    }

    @Test
    void recusadoNaoMexeNoEstoque() {
        Compra compra = compraService.registrar(pedido(4), OrigemCompra.API);

        compraService.aplicarResultado(resultado(compra.getId(), StatusPagamento.RECUSADO));

        assertThat(compraService.buscar(compra.getId()).getStatus()).isEqualTo(StatusCompra.RECUSADO);
        assertThat(produtos.findById(banana.getId()).orElseThrow().getEstoque()).isEqualTo(10);
    }

    @Test
    void falhaDeSmtpERegistradaSemDesfazerACompra() {
        Compra compra = compraService.registrar(pedido(1), OrigemCompra.API);

        listener.receber(resultado(compra.getId(), StatusPagamento.APROVADO), String.valueOf(compra.getId()));

        assertThat(compraService.buscar(compra.getId()).getStatus()).isEqualTo(StatusCompra.APROVADO);
        List<EmailEnviado> registros = emails.findByCompraIdOrderByDataEnvioDesc(compra.getId());
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().getStatus()).isEqualTo(StatusEmail.FALHA);
        assertThat(registros.getFirst().getErro()).isNotBlank();
        assertThat(registros.getFirst().getAssunto()).contains("aprovado");
    }

    @Test
    void recusaPedidoMaiorQueOEstoque() {
        assertThatThrownBy(() -> compraService.registrar(pedido(11), OrigemCompra.API))
                .isInstanceOf(CompraInvalidaException.class)
                .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void apiDevolve202ComLocationEConsultaOStatus() throws Exception {
        String corpo = """
                {"cliente": {"nome": "Ana Souza", "email": "ana@exemplo.com", "cpf": "529.982.247-25"},
                 "pagamento": {"numeroCartao": "4111 1111 1111 1111", "titular": "ANA SOUZA"},
                 "itens": [{"produtoId": %d, "quantidade": 2}]}
                """.formatted(banana.getId());

        String location = mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorTotal").value(15.98))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.itens[0].produto").value("Banana prata"));
    }

    @Test
    void apiValidaCorpoECompraInexistente() throws Exception {
        mvc.perform(post("/api/compras").contentType(MediaType.APPLICATION_JSON).content("{\"itens\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cliente").exists());

        mvc.perform(get("/api/compras/999999"))
                .andExpect(status().isNotFound());
    }
}
