package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.dto.CompraRequest;
import br.com.fiap.mercadoexpress.dto.CompraResponse;
import br.com.fiap.mercadoexpress.dto.ProdutoResponse;
import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.OrigemCompra;
import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.service.CompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API REST usada pelo Postman e pelo scripts/teste-fluxo.sh.
 *
 * <p>POST /api/compras devolve 202 Accepted: a compra foi aceita e gravada
 * como PENDENTE, mas o pagamento ainda vai ser processado de forma
 * assincrona. O cliente acompanha o status em GET /api/compras/{id}.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompraApiController {

    private final CompraService compraService;
    private final EmailEnviadoRepository emails;
    private final ProdutoRepository produtos;

    @PostMapping("/compras")
    public ResponseEntity<CompraResponse> criar(@Valid @RequestBody CompraRequest pedido) {
        Compra compra = compraService.registrar(pedido, OrigemCompra.API);
        return ResponseEntity.accepted()
                .location(URI.create("/api/compras/" + compra.getId()))
                .body(CompraResponse.de(compraService.buscar(compra.getId()), List.of()));
    }

    @GetMapping("/compras/{id}")
    public CompraResponse consultar(@PathVariable Long id) {
        return CompraResponse.de(compraService.buscar(id), emails.findByCompraIdOrderByDataEnvioDesc(id));
    }

    /** Catalogo resumido, para descobrir os produtoId ao montar uma compra. */
    @GetMapping("/produtos")
    public List<ProdutoResponse> produtos() {
        return produtos.findByAtivoTrueOrderBySetorAscNomeAsc().stream().map(ProdutoResponse::de).toList();
    }
}
