package br.com.fiap.bancotranquilo.controller;

import br.com.fiap.bancotranquilo.config.RegrasBancoProperties;
import br.com.fiap.bancotranquilo.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.dto.PagamentoResponse;
import br.com.fiap.bancotranquilo.service.AnalisePagamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
public class PagamentoController {

    private final AnalisePagamentoService analise;
    private final RegrasBancoProperties regras;

    /** Autoriza ou recusa um pagamento. Corpo invalido devolve 400 (ProblemDetail). */
    @PostMapping
    public PagamentoResponse autorizar(@Valid @RequestBody PagamentoRequest pedido) {
        return analise.analisar(pedido);
    }

    /** Mostra as regras em vigor: util para conferir antes de gravar a demonstracao. */
    @GetMapping("/regras")
    public Map<String, Object> regras() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("recusaValorAcimaDe", regras.getLimiteValor());
        mapa.put("recusaCartaoTerminadoEm", regras.getSufixoCartaoRecusado());
        mapa.put("recusaCpfs", regras.getCpfsBloqueados());
        mapa.put("atrasoMs", regras.getAtrasoMinimoMs() + "-" + regras.getAtrasoMaximoMs());
        mapa.put("demaisCasos", "APROVADO");
        return mapa;
    }
}
