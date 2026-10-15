package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.dto.PainelGerencia;
import br.com.fiap.mercadoexpress.service.GerenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** GET /gerencia - dashboard de vendas, estoque, pagamentos e e-mails. */
@Controller
@RequiredArgsConstructor
public class GerenciaController {

    private static final List<Integer> PERIODOS = List.of(7, 15, 30);

    private final GerenciaService gerencia;
    private final JsonMapper json;

    @GetMapping("/gerencia")
    public String painel(@RequestParam(defaultValue = "30") int dias, Model model) {
        int periodo = PERIODOS.contains(dias) ? dias : 30;
        PainelGerencia painel = gerencia.montar(periodo);
        model.addAttribute("painel", painel);
        model.addAttribute("periodos", PERIODOS);
        model.addAttribute("graficosJson", json.writeValueAsString(painel.getGraficos()));
        return "gerencia/painel";
    }
}
