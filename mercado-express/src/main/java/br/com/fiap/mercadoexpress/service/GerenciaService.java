package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.dto.PainelGerencia;
import br.com.fiap.mercadoexpress.dto.PainelGerencia.CompraResumo;
import br.com.fiap.mercadoexpress.dto.PainelGerencia.ProdutoVendido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numeros do dashboard de gerencia. Agregacoes em SQL puro (JdbcClient), que
 * e o jeito mais direto de somar, agrupar por dia e ranquear; faturamento e
 * lucro consideram so compras APROVADAS.
 */
@Service
@RequiredArgsConstructor
public class GerenciaService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;
    private final ProdutoRepository produtos;
    private final EmailEnviadoRepository emails;

    @Transactional(readOnly = true)
    public PainelGerencia montar(int dias) {
        LocalDate hoje = LocalDate.now();
        LocalDate primeiroDia = hoje.minusDays(dias - 1L);
        Map<String, Object> inicio = Map.of("inicio", primeiroDia.atStartOfDay());

        // ---- KPIs -------------------------------------------------------
        Map<String, Object> kpi = jdbc.sql("""
                        SELECT COALESCE(SUM(CASE WHEN STATUS = 'APROVADO' THEN VALOR_TOTAL END), 0) AS FATURAMENTO,
                               COALESCE(SUM(CASE WHEN STATUS = 'APROVADO' THEN VALOR_TOTAL - CUSTO_TOTAL END), 0) AS LUCRO,
                               COALESCE(SUM(CASE WHEN STATUS = 'APROVADO' THEN 1 ELSE 0 END), 0) AS APROVADOS,
                               COALESCE(SUM(CASE WHEN STATUS = 'RECUSADO' THEN 1 ELSE 0 END), 0) AS RECUSADOS,
                               COALESCE(SUM(CASE WHEN STATUS = 'PENDENTE' THEN 1 ELSE 0 END), 0) AS PENDENTES
                          FROM TDS_COMPRAS_MERCADO
                         WHERE DATA_CRIACAO >= :inicio
                        """)
                .params(inicio)
                .query((rs, n) -> Map.<String, Object>of(
                        "faturamento", rs.getBigDecimal("FATURAMENTO"),
                        "lucro", rs.getBigDecimal("LUCRO"),
                        "aprovados", rs.getLong("APROVADOS"),
                        "recusados", rs.getLong("RECUSADOS"),
                        "pendentes", rs.getLong("PENDENTES")))
                .single();
        BigDecimal faturamento = (BigDecimal) kpi.get("faturamento");
        BigDecimal lucro = (BigDecimal) kpi.get("lucro");
        long aprovados = (Long) kpi.get("aprovados");
        long recusados = (Long) kpi.get("recusados");
        long pendentes = (Long) kpi.get("pendentes");

        // ---- Series por dia (dias sem venda entram com zero) ------------
        Map<LocalDate, long[]> pedidosDia = new HashMap<>();
        Map<LocalDate, BigDecimal[]> valoresDia = new HashMap<>();
        jdbc.sql("""
                        SELECT CAST(DATA_CRIACAO AS DATE) AS DIA, STATUS, COUNT(*) AS PEDIDOS,
                               SUM(VALOR_TOTAL) AS VALOR, SUM(VALOR_TOTAL - CUSTO_TOTAL) AS LUCRO
                          FROM TDS_COMPRAS_MERCADO
                         WHERE DATA_CRIACAO >= :inicio
                         GROUP BY CAST(DATA_CRIACAO AS DATE), STATUS
                        """)
                .params(inicio)
                .query(rs -> {
                    LocalDate dia = rs.getDate("DIA").toLocalDate();
                    String status = rs.getString("STATUS");
                    long[] contagem = pedidosDia.computeIfAbsent(dia, d -> new long[2]);
                    if ("APROVADO".equals(status)) {
                        contagem[0] += rs.getLong("PEDIDOS");
                        valoresDia.put(dia, new BigDecimal[]{rs.getBigDecimal("VALOR"), rs.getBigDecimal("LUCRO")});
                    } else if ("RECUSADO".equals(status)) {
                        contagem[1] += rs.getLong("PEDIDOS");
                    }
                });

        List<String> rotulos = new ArrayList<>();
        List<Long> serieAprovados = new ArrayList<>();
        List<Long> serieRecusados = new ArrayList<>();
        List<BigDecimal> serieFaturamento = new ArrayList<>();
        List<BigDecimal> serieLucro = new ArrayList<>();
        for (LocalDate dia = primeiroDia; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            long[] contagem = pedidosDia.getOrDefault(dia, new long[2]);
            BigDecimal[] valores = valoresDia.getOrDefault(dia, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            rotulos.add(dia.format(DIA_MES));
            serieAprovados.add(contagem[0]);
            serieRecusados.add(contagem[1]);
            serieFaturamento.add(valores[0]);
            serieLucro.add(valores[1]);
        }

        // ---- Top produtos ------------------------------------------------
        List<ProdutoVendido> top = jdbc.sql("""
                        SELECT P.NOME, SUM(I.QUANTIDADE) AS QTD, SUM(I.SUBTOTAL) AS RECEITA,
                               SUM(I.SUBTOTAL - I.CUSTO_UNITARIO * I.QUANTIDADE) AS LUCRO
                          FROM TDS_ITENS_COMPRA_MERCADO I
                          JOIN TDS_COMPRAS_MERCADO C ON C.ID = I.COMPRA_ID
                          JOIN TDS_PRODUTOS_MERCADO P ON P.ID = I.PRODUTO_ID
                         WHERE C.STATUS = 'APROVADO' AND C.DATA_CRIACAO >= :inicio
                         GROUP BY P.NOME
                         ORDER BY RECEITA DESC
                         LIMIT 8
                        """)
                .params(inicio)
                .query((rs, n) -> new ProdutoVendido(rs.getString("NOME"), rs.getLong("QTD"),
                        rs.getBigDecimal("RECEITA"), rs.getBigDecimal("LUCRO")))
                .list();

        // ---- Estoque -----------------------------------------------------
        List<Produto> estoque = produtos.findByAtivoTrueOrderBySetorAscNomeAsc().stream()
                .sorted(Comparator.comparing(Produto::getEstoque))
                .toList();
        List<Produto> estoqueBaixo = estoque.stream().filter(Produto::isEstoqueBaixo).toList();

        // ---- Compras recentes (todas, independente do periodo) -----------
        List<CompraResumo> recentes = jdbc.sql("""
                        SELECT C.ID, C.CLIENTE_NOME, C.ORIGEM, C.VALOR_TOTAL, C.STATUS, C.MOTIVO, C.DATA_CRIACAO,
                               (SELECT COALESCE(SUM(I.QUANTIDADE), 0) FROM TDS_ITENS_COMPRA_MERCADO I
                                 WHERE I.COMPRA_ID = C.ID) AS ITENS
                          FROM TDS_COMPRAS_MERCADO C
                         ORDER BY C.DATA_CRIACAO DESC, C.ID DESC
                         LIMIT 12
                        """)
                .query((rs, n) -> new CompraResumo(rs.getLong("ID"), rs.getString("CLIENTE_NOME"),
                        rs.getString("ORIGEM"), rs.getLong("ITENS"), rs.getBigDecimal("VALOR_TOTAL"),
                        rs.getString("STATUS"), rs.getString("MOTIVO"),
                        rs.getTimestamp("DATA_CRIACAO").toLocalDateTime()))
                .list();

        // ---- Dados dos graficos -------------------------------------------
        Map<String, Object> graficos = new LinkedHashMap<>();
        graficos.put("dias", rotulos);
        graficos.put("aprovados", serieAprovados);
        graficos.put("recusados", serieRecusados);
        graficos.put("faturamento", serieFaturamento);
        graficos.put("lucro", serieLucro);
        graficos.put("topNomes", top.stream().map(ProdutoVendido::getNome).toList());
        graficos.put("topReceita", top.stream().map(ProdutoVendido::getReceita).toList());
        graficos.put("topQuantidade", top.stream().map(ProdutoVendido::getQuantidade).toList());
        graficos.put("estoqueNomes", estoque.stream().map(Produto::getNome).toList());
        graficos.put("estoqueQtd", estoque.stream().map(Produto::getEstoque).toList());
        graficos.put("estoqueMinimo", estoque.stream().map(Produto::getEstoqueMinimo).toList());
        graficos.put("estoqueBaixo", estoque.stream().map(Produto::isEstoqueBaixo).toList());
        graficos.put("status", List.of(aprovados, recusados, pendentes));

        long decididos = aprovados + recusados;
        return PainelGerencia.builder()
                .dias(dias)
                .faturamento(faturamento)
                .lucro(lucro)
                .margemPercentual(percentual(lucro, faturamento))
                .ticketMedio(aprovados == 0 ? BigDecimal.ZERO
                        : faturamento.divide(BigDecimal.valueOf(aprovados), 2, RoundingMode.HALF_UP))
                .aprovados(aprovados)
                .recusados(recusados)
                .pendentes(pendentes)
                .taxaAprovacao(percentual(BigDecimal.valueOf(aprovados), BigDecimal.valueOf(decididos)))
                .topProdutos(top)
                .estoque(estoque)
                .estoqueBaixo(estoqueBaixo)
                .ultimosEmails(emails.findTop8ByOrderByDataEnvioDescIdDesc())
                .comprasRecentes(recentes)
                .graficos(graficos)
                .build();
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (todo.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(CEM).divide(todo, 1, RoundingMode.HALF_UP);
    }
}
