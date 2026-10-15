package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.fiap.mercadoexpress.service.CompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * GET /pedidos/{id} - acompanhamento do pedido. Enquanto estiver PENDENTE a
 * pagina consulta GET /api/compras/{id} a cada segundo (js/pedido.js) e se
 * recarrega quando o Banco Tranquilo responde.
 */
@Controller
@RequiredArgsConstructor
public class PedidoController {

    private final CompraService compraService;
    private final EmailEnviadoRepository emails;

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("compra", compraService.buscar(id));
        model.addAttribute("emails", emails.findByCompraIdOrderByDataEnvioDesc(id));
        return "pedido/detalhe";
    }
}
