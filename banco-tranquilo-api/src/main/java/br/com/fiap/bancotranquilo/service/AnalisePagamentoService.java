package br.com.fiap.bancotranquilo.service;

import br.com.fiap.bancotranquilo.config.RegrasBancoProperties;
import br.com.fiap.bancotranquilo.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.dto.PagamentoResponse;
import br.com.fiap.bancotranquilo.dto.StatusTransacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Regras deterministicas do Banco Tranquilo, avaliadas nesta ordem:
 * <ol>
 *     <li>valor acima de {@code banco.limite-valor} (R$ 5.000,00) - RECUSADO;</li>
 *     <li>cartao terminado em {@code banco.sufixo-cartao-recusado} (0000) - RECUSADO;</li>
 *     <li>CPF na lista {@code banco.cpfs-bloqueados} - RECUSADO;</li>
 *     <li>qualquer outro caso - APROVADO com codigo de autorizacao.</li>
 * </ol>
 * Antes de responder, espera entre 500 e 1500 ms para simular a latencia de
 * uma adquirente de verdade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalisePagamentoService {

    private static final NumberFormat REAL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private final RegrasBancoProperties regras;

    public PagamentoResponse analisar(PagamentoRequest pedido) {
        simularLatencia();

        Optional<String> recusa = motivoDeRecusa(pedido);
        PagamentoResponse resposta = PagamentoResponse.builder()
                .transacaoId(UUID.randomUUID().toString())
                .compraId(pedido.getCompraId())
                .status(recusa.isPresent() ? StatusTransacao.RECUSADO : StatusTransacao.APROVADO)
                .codigoAutorizacao(recusa.isPresent() ? null : novoCodigoAutorizacao())
                .motivo(recusa.orElse("Pagamento aprovado pelo Banco Tranquilo"))
                .processadoEm(LocalDateTime.now())
                .build();

        log.info("Compra {} -> {} ({})", pedido.getCompraId(), resposta.getStatus(), resposta.getMotivo());
        return resposta;
    }

    /** Regras puras, sem atraso: e o que os testes exercitam. */
    public Optional<String> motivoDeRecusa(PagamentoRequest pedido) {
        if (pedido.getValor().compareTo(regras.getLimiteValor()) > 0) {
            return Optional.of("Valor acima do limite de " + REAL.format(regras.getLimiteValor()) + " por transação");
        }
        if (somenteDigitos(pedido.getNumeroCartao()).endsWith(regras.getSufixoCartaoRecusado())) {
            return Optional.of("Cartão recusado pelo emissor");
        }
        if (regras.getCpfsBloqueados().contains(somenteDigitos(pedido.getCpf()))) {
            return Optional.of("CPF com restrição cadastral");
        }
        return Optional.empty();
    }

    private void simularLatencia() {
        long minimo = regras.getAtrasoMinimoMs();
        long maximo = Math.max(minimo, regras.getAtrasoMaximoMs());
        if (maximo <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minimo, maximo + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String novoCodigoAutorizacao() {
        return "BT" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
