package br.com.fiap.mercadocommons.mensageria;

/**
 * Nomes de exchanges, filas e routing keys usados por todos os servicos.
 *
 * <p>Ficam num lugar so para que produtor e consumidor nunca divirjam por
 * causa de um erro de digitacao. A routing key de cada fila tem o mesmo nome
 * da fila, o que deixa o painel do RabbitMQ facil de ler.</p>
 */
public final class RabbitNomes {

    /** Exchange principal (topic) por onde passam as mensagens de pagamento. */
    public static final String EXCHANGE_PAGAMENTOS = "mercado.pagamentos";

    /** Dead letter exchange: recebe o que os consumidores desistiram de processar. */
    public static final String EXCHANGE_PAGAMENTOS_DLX = "mercado.pagamentos.dlx";

    /** mercado-express -> pagamento-integracao */
    public static final String FILA_PAGAMENTO_SOLICITADO = "pagamento.solicitado";
    public static final String RK_PAGAMENTO_SOLICITADO = "pagamento.solicitado";

    /** pagamento-integracao -> mercado-express */
    public static final String FILA_PAGAMENTO_PROCESSADO = "pagamento.processado";
    public static final String RK_PAGAMENTO_PROCESSADO = "pagamento.processado";

    /** Solicitacoes que esgotaram as tentativas (ex.: banco fora do ar). */
    public static final String FILA_PAGAMENTO_SOLICITADO_DLQ = "pagamento.solicitado.dlq";
    public static final String RK_PAGAMENTO_SOLICITADO_DLQ = "pagamento.solicitado.dlq";

    /** Resultados que o mercado nao conseguiu aplicar (ex.: PostgreSQL fora). */
    public static final String FILA_PAGAMENTO_PROCESSADO_DLQ = "pagamento.processado.dlq";
    public static final String RK_PAGAMENTO_PROCESSADO_DLQ = "pagamento.processado.dlq";

    private RabbitNomes() {
    }
}
