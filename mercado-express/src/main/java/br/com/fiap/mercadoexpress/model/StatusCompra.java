package br.com.fiap.mercadoexpress.model;

/**
 * Ciclo de vida de uma compra. Toda compra nasce PENDENTE; so a resposta do
 * Banco Tranquilo (via fila pagamento.processado) a leva a APROVADO ou
 * RECUSADO, e dali ela nao sai mais.
 */
public enum StatusCompra {
    PENDENTE("Aguardando pagamento"),
    APROVADO("Pagamento aprovado"),
    RECUSADO("Pagamento recusado");

    private final String rotulo;

    StatusCompra(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean isFinal() {
        return this != PENDENTE;
    }
}
