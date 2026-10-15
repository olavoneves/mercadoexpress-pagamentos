package br.com.fiap.mercadoexpress.mensageria;

import br.com.fiap.mercadocommons.evento.PagamentoSolicitadoEvent;
import br.com.fiap.mercadocommons.mensageria.MensagemPersistente;
import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.repository.CompraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.EXCHANGE_PAGAMENTOS;
import static br.com.fiap.mercadocommons.mensageria.RabbitNomes.RK_PAGAMENTO_SOLICITADO;

/**
 * Publica pagamento.solicitado SO DEPOIS do commit da compra.
 *
 * <p>Se publicasse dentro da transacao, o banco mock poderia responder antes
 * do commit e o consumidor do resultado nao encontraria a compra. Com
 * AFTER_COMMIT a ordem e garantida: grava, confirma, publica.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoSolicitadoPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final CompraRepository compras;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CompraRegistrada registrada) {
        Compra compra = compras.findById(registrada.getCompraId()).orElseThrow();

        PagamentoSolicitadoEvent evento = PagamentoSolicitadoEvent.builder()
                .compraId(compra.getId())
                .valor(compra.getValorTotal())
                .clienteNome(compra.getClienteNome())
                .clienteEmail(compra.getClienteEmail())
                .clienteCpf(compra.getClienteCpf())
                .numeroCartao(registrada.getNumeroCartao())
                .titularCartao(registrada.getTitularCartao())
                .solicitadoEm(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(EXCHANGE_PAGAMENTOS, RK_PAGAMENTO_SOLICITADO, evento,
                MensagemPersistente.comCorrelacao(compra.getId()));

        log.info("Compra {}: PENDENTE, R$ {} publicado em {}", compra.getId(), compra.getValorTotal(),
                RK_PAGAMENTO_SOLICITADO);
    }
}
