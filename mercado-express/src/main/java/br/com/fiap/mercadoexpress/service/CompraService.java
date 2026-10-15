package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadocommons.evento.PagamentoProcessadoEvent;
import br.com.fiap.mercadocommons.evento.StatusPagamento;
import br.com.fiap.mercadoexpress.dto.CompraRequest;
import br.com.fiap.mercadoexpress.exception.CompraInvalidaException;
import br.com.fiap.mercadoexpress.exception.CompraNaoEncontradaException;
import br.com.fiap.mercadoexpress.mensageria.CompraRegistrada;
import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.ItemCompra;
import br.com.fiap.mercadoexpress.model.OrigemCompra;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusCompra;
import br.com.fiap.mercadoexpress.repository.CompraRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Regras da compra: montar e gravar como PENDENTE, e aplicar a resposta do
 * banco quando ela chegar pela fila.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compras;
    private final ProdutoRepository produtos;
    private final ApplicationEventPublisher eventos;

    /**
     * Grava a compra como PENDENTE e agenda a publicacao em
     * pagamento.solicitado para depois do commit. Nao espera o banco:
     * quem chamou recebe a compra na hora (HTTP 202 na API).
     */
    @Transactional
    public Compra registrar(CompraRequest pedido, OrigemCompra origem) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        pedido.getItens().forEach(i -> quantidades.merge(i.getProdutoId(), i.getQuantidade(), Integer::sum));

        String cartao = pedido.getPagamento().getNumeroCartao().replaceAll("\\D", "");
        Compra compra = Compra.builder()
                .clienteNome(pedido.getCliente().getNome().trim())
                .clienteEmail(pedido.getCliente().getEmail().trim().toLowerCase())
                .clienteCpf(pedido.getCliente().getCpf().trim())
                .cartaoFinal(cartao.substring(cartao.length() - 4))
                .status(StatusCompra.PENDENTE)
                .origem(origem)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entrada : quantidades.entrySet()) {
            Produto produto = produtos.findById(entrada.getKey())
                    .filter(Produto::getAtivo)
                    .orElseThrow(() -> new CompraInvalidaException(
                            "Produto " + entrada.getKey() + " não existe ou está fora de linha"));
            int quantidade = entrada.getValue();
            if (produto.getEstoque() < quantidade) {
                throw new CompraInvalidaException("Estoque insuficiente para " + produto.getNome()
                        + ": pedido " + quantidade + ", disponível " + produto.getEstoque());
            }
            BigDecimal subtotal = produto.getPrecoVenda().multiply(BigDecimal.valueOf(quantidade));
            compra.adicionarItem(ItemCompra.builder()
                    .produto(produto)
                    .quantidade(quantidade)
                    .precoUnitario(produto.getPrecoVenda())
                    .custoUnitario(produto.getPrecoCusto())
                    .subtotal(subtotal)
                    .build());
            total = total.add(subtotal);
            custo = custo.add(produto.getPrecoCusto().multiply(BigDecimal.valueOf(quantidade)));
        }
        compra.setValorTotal(total);
        compra.setCustoTotal(custo);

        Compra salva = compras.save(compra);
        eventos.publishEvent(new CompraRegistrada(salva.getId(), cartao, pedido.getPagamento().getTitular()));
        return salva;
    }

    /**
     * Aplica a resposta do banco. Idempotente: se a compra ja nao esta
     * PENDENTE (mensagem duplicada ou reentregue), nada muda.
     *
     * @return o id da compra quando o status mudou (e o e-mail deve sair);
     *         vazio quando a mensagem foi ignorada
     */
    @Transactional
    public Optional<Long> aplicarResultado(PagamentoProcessadoEvent resultado) {
        Optional<Compra> encontrada = compras.travarParaAtualizacao(resultado.getCompraId());
        if (encontrada.isEmpty()) {
            log.warn("Compra {}: resultado recebido para compra inexistente, ignorado", resultado.getCompraId());
            return Optional.empty();
        }
        Compra compra = encontrada.get();
        if (compra.getStatus() != StatusCompra.PENDENTE) {
            log.info("Compra {}: ja esta {}, resultado {} ignorado (idempotencia)",
                    compra.getId(), compra.getStatus(), resultado.getStatus());
            return Optional.empty();
        }

        boolean aprovado = resultado.getStatus() == StatusPagamento.APROVADO;
        compra.setStatus(aprovado ? StatusCompra.APROVADO : StatusCompra.RECUSADO);
        compra.setCodigoAutorizacao(resultado.getCodigoAutorizacao());
        compra.setMotivo(resultado.getMotivo());
        compra.setTransacaoId(resultado.getTransacaoId());

        if (aprovado) {
            for (ItemCompra item : compra.getItens()) {
                int baixou = produtos.baixarEstoque(item.getProduto().getId(), item.getQuantidade());
                if (baixou == 0) {
                    log.warn("Compra {}: estoque de {} insuficiente na baixa (vendido entre o pedido e a aprovacao)",
                            compra.getId(), item.getProduto().getNome());
                }
            }
        }
        compras.save(compra);
        log.info("Compra {}: {} ({})", compra.getId(), compra.getStatus(), compra.getMotivo());
        return Optional.of(compra.getId());
    }

    @Transactional(readOnly = true)
    public Compra buscar(Long id) {
        return compras.buscarCompleta(id).orElseThrow(() -> new CompraNaoEncontradaException(id));
    }
}
