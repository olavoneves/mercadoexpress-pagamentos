package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.exception.CompraInvalidaException;
import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.OrigemCompra;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.service.CompraService;
import br.com.fiap.mercadoexpress.web.Carrinho;
import br.com.fiap.mercadoexpress.web.CheckoutForm;
import br.com.fiap.mercadoexpress.web.LinhaCarrinho;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carrinho em Thymeleaf: adicionar, alterar quantidade, remover e finalizar.
 * Finalizar usa o mesmo CompraService da API, entao a compra da loja segue o
 * mesmo caminho assincrono: PENDENTE -> RabbitMQ -> banco -> resultado.
 */
@Controller
@RequestMapping("/carrinho")
@RequiredArgsConstructor
public class CarrinhoController {

    private final Carrinho carrinho;
    private final ProdutoRepository produtos;
    private final CompraService compraService;

    @GetMapping
    public String ver(Model model) {
        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", new CheckoutForm());
        }
        return montarTela(model);
    }

    @PostMapping("/adicionar")
    public String adicionar(@RequestParam Long produtoId,
                            @RequestParam(defaultValue = "1") int quantidade,
                            @RequestParam(defaultValue = "/") String voltar,
                            RedirectAttributes flash) {
        Optional<Produto> encontrado = produtos.findById(produtoId).filter(Produto::getAtivo);
        if (encontrado.isEmpty()) {
            flash.addFlashAttribute("falha", "Esse produto não está mais na vitrine.");
            return "redirect:/";
        }
        Produto produto = encontrado.get();
        int desejado = carrinho.quantidadeDe(produtoId) + Math.max(1, quantidade);
        if (desejado > produto.getEstoque()) {
            carrinho.alterar(produtoId, produto.getEstoque());
            flash.addFlashAttribute("falha", produto.getEstoque() == 0
                    ? produto.getNome() + " está sem estoque hoje."
                    : "Só temos " + produto.getEstoque() + " de " + produto.getNome() + "; ajustamos o carrinho.");
        } else {
            carrinho.alterar(produtoId, desejado);
            flash.addFlashAttribute("sucesso", produto.getNome() + " foi para o carrinho ("
                    + desejado + (desejado == 1 ? " unidade)." : " unidades)."));
        }
        // so aceita caminho interno, para nao virar redirecionamento aberto
        return "redirect:" + (voltar.startsWith("/") && !voltar.startsWith("//") ? voltar : "/");
    }

    @PostMapping("/alterar")
    public String alterar(@RequestParam Long produtoId, @RequestParam int quantidade, RedirectAttributes flash) {
        produtos.findById(produtoId).ifPresent(produto -> {
            int ajustada = Math.min(quantidade, produto.getEstoque());
            carrinho.alterar(produtoId, ajustada);
            if (ajustada < quantidade) {
                flash.addFlashAttribute("falha", "Só temos " + produto.getEstoque() + " de " + produto.getNome() + ".");
            }
        });
        return "redirect:/carrinho";
    }

    @PostMapping("/remover")
    public String remover(@RequestParam Long produtoId, RedirectAttributes flash) {
        carrinho.remover(produtoId);
        flash.addFlashAttribute("sucesso", "Item removido do carrinho.");
        return "redirect:/carrinho";
    }

    @PostMapping("/finalizar")
    public String finalizar(@Valid @ModelAttribute("checkoutForm") CheckoutForm form, BindingResult erros,
                            Model model, RedirectAttributes flash) {
        if (carrinho.isVazio()) {
            flash.addFlashAttribute("falha", "Seu carrinho está vazio.");
            return "redirect:/";
        }
        if (erros.hasErrors()) {
            return montarTela(model);
        }
        try {
            Compra compra = compraService.registrar(form.paraCompra(carrinho.getItens()), OrigemCompra.LOJA);
            carrinho.limpar();
            flash.addFlashAttribute("sucesso",
                    "Pedido recebido! Estamos aguardando a resposta do Banco Tranquilo.");
            return "redirect:/pedidos/" + compra.getId();
        } catch (CompraInvalidaException e) {
            flash.addFlashAttribute("falha", e.getMessage());
            return "redirect:/carrinho";
        }
    }

    private String montarTela(Model model) {
        List<LinhaCarrinho> linhas = new ArrayList<>();
        for (Map.Entry<Long, Integer> item : carrinho.getItens().entrySet()) {
            produtos.findById(item.getKey()).ifPresent(p -> linhas.add(new LinhaCarrinho(p, item.getValue())));
        }
        BigDecimal total = linhas.stream().map(LinhaCarrinho::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("linhas", linhas);
        model.addAttribute("total", total);
        model.addAttribute("cartoesDeTeste", CheckoutForm.cartoesDeTeste());
        return "carrinho/index";
    }
}
