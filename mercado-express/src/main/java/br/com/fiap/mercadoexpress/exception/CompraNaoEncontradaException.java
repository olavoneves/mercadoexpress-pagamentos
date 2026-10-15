package br.com.fiap.mercadoexpress.exception;

public class CompraNaoEncontradaException extends RuntimeException {

    public CompraNaoEncontradaException(Long id) {
        super("Compra " + id + " não encontrada");
    }
}
