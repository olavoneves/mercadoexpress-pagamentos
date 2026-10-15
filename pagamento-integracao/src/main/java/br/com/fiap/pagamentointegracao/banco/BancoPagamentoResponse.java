package br.com.fiap.pagamentointegracao.banco;

import br.com.fiap.mercadocommons.evento.StatusPagamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Contrato de saida do POST /api/pagamentos do Banco Tranquilo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BancoPagamentoResponse {

    private String transacaoId;
    private Long compraId;
    private StatusPagamento status;
    private String codigoAutorizacao;
    private String motivo;
    private LocalDateTime processadoEm;
}
