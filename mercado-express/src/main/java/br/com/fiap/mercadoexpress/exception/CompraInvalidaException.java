package br.com.fiap.mercadoexpress.exception;

/** Regra de negocio violada ao montar a compra (produto inexistente, sem estoque...). */
public class CompraInvalidaException extends RuntimeException {

    public CompraInvalidaException(String mensagem) {
        super(mensagem);
    }
}
