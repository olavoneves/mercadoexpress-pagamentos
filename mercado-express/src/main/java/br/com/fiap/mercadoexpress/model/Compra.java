package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido do cliente. Os valores (total e custo) sao congelados no momento da
 * compra, para que uma mudanca de preco depois nao altere o historico nem o
 * dashboard. Do cartao so guardamos os 4 ultimos digitos.
 */
@Entity
@Table(name = "TDS_COMPRAS_MERCADO")
@Getter
@Setter
@Builder
@ToString(exclude = "itens")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "CLIENTE_NOME", nullable = false, length = 100)
    private String clienteNome;

    @Column(name = "CLIENTE_EMAIL", nullable = false, length = 120)
    private String clienteEmail;

    @Column(name = "CLIENTE_CPF", nullable = false, length = 14)
    private String clienteCpf;

    @Column(name = "CARTAO_FINAL", nullable = false, length = 4)
    private String cartaoFinal;

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "CUSTO_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal custoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private StatusCompra status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", nullable = false, length = 10)
    private OrigemCompra origem;

    @Column(name = "CODIGO_AUTORIZACAO", length = 20)
    private String codigoAutorizacao;

    @Column(name = "MOTIVO", length = 255)
    private String motivo;

    @Column(name = "TRANSACAO_ID", length = 40)
    private String transacaoId;

    @Column(name = "DATA_CRIACAO", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "DATA_ATUALIZACAO", nullable = false)
    private LocalDateTime dataAtualizacao;

    @Builder.Default
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemCompra> itens = new ArrayList<>();

    public void adicionarItem(ItemCompra item) {
        item.setCompra(this);
        itens.add(item);
    }

    public BigDecimal getLucro() {
        return valorTotal.subtract(custoTotal);
    }

    public int getQuantidadeItens() {
        return itens.stream().mapToInt(ItemCompra::getQuantidade).sum();
    }

    @PrePersist
    private void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        if (dataCriacao == null) {
            dataCriacao = agora;
        }
        dataAtualizacao = agora;
        if (status == null) {
            status = StatusCompra.PENDENTE;
        }
    }

    @PreUpdate
    private void aoAtualizar() {
        dataAtualizacao = LocalDateTime.now();
    }
}
