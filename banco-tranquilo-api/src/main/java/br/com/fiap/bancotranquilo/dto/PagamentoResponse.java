package br.com.fiap.bancotranquilo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Resposta de POST /api/pagamentos: sempre 200, aprovado ou recusado. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoResponse {

    private String transacaoId;
    private Long compraId;
    private StatusTransacao status;
    /** Codigo de autorizacao; nulo quando RECUSADO. */
    private String codigoAutorizacao;
    private String motivo;
    private LocalDateTime processadoEm;
}
