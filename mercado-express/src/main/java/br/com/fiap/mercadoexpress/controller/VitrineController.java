package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** GET / - vitrine com os produtos ativos, filtravel por setor. */
@Controller
@RequiredArgsConstructor
public class VitrineController {

    private final ProdutoRepository produtos;

    @GetMapping("/")
    public String vitrine(@RequestParam(required = false) String setor, Model model) {
        List<Produto> ativos = produtos.findByAtivoTrueOrderBySetorAscNomeAsc();
        List<String> setores = ativos.stream().map(Produto::getSetor).distinct().sorted().toList();
        boolean filtrar = setor != null && !setor.isBlank();

        model.addAttribute("produtos", filtrar
                ? ativos.stream().filter(p -> p.getSetor().equals(setor)).toList()
                : ativos);
        model.addAttribute("setores", setores);
        model.addAttribute("setorSelecionado", filtrar ? setor : null);
        return "vitrine/index";
    }
}
