package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.exception.CompraInvalidaException;
import br.com.fiap.mercadoexpress.exception.CompraNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Erros da API REST em formato ProblemDetail (RFC 9457). So vale para /api. */
@RestControllerAdvice(assignableTypes = CompraApiController.class)
public class TratadorDeErrosApi {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados da compra inválidos");
        problema.setTitle("Requisição inválida");
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(CompraInvalidaException.class)
    public ProblemDetail compraInvalida(CompraInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problema.setTitle("Compra não aceita");
        return problema;
    }

    @ExceptionHandler(CompraNaoEncontradaException.class)
    public ProblemDetail naoEncontrada(CompraNaoEncontradaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Compra não encontrada");
        return problema;
    }
}
