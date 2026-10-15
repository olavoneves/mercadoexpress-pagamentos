package br.com.fiap.mercadocommons.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Publicado pelo pagamento-integracao em pagamento.processado com a resposta
 * do Banco Tranquilo. Consumido pelo mercado-express.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoProcessadoEvent {

    private Long compraId;
    private StatusPagamento status;
    /** Preenchido so quando APROVADO. */
    private String codigoAutorizacao;
    /** Motivo da recusa, ou a mensagem de aprovacao do banco. */
    private String motivo;
    private String transacaoId;
    private LocalDateTime processadoEm;
}
