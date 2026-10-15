package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.exception.CompraNaoEncontradaException;
import br.com.fiap.mercadoexpress.web.Carrinho;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Dados do cabecalho (contador do carrinho) e erro 404 das telas Thymeleaf. */
@ControllerAdvice(assignableTypes = {VitrineController.class, CarrinhoController.class,
        PedidoController.class, GerenciaController.class})
@RequiredArgsConstructor
public class DadosComunsDasTelas {

    private final Carrinho carrinho;

    @ModelAttribute("qtdCarrinho")
    public int quantidadeNoCarrinho() {
        return carrinho.getQuantidadeTotal();
    }

    @ExceptionHandler(CompraNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String pedidoNaoEncontrado(CompraNaoEncontradaException e, Model model) {
        model.addAttribute("qtdCarrinho", carrinho.getQuantidadeTotal());
        model.addAttribute("mensagem", e.getMessage());
        return "erro/404";
    }
}
