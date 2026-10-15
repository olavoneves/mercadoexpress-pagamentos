package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/** Linha do pedido, com preco de venda e de custo copiados do produto na hora da compra. */
@Entity
@Table(name = "TDS_ITENS_COMPRA_MERCADO")
@Getter
@Setter
@Builder
@ToString(exclude = {"compra", "produto"})
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "COMPRA_ID", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUTO_ID", nullable = false)
    private Produto produto;

    @Column(name = "QUANTIDADE", nullable = false)
    private Integer quantidade;

    @Column(name = "PRECO_UNITARIO", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "CUSTO_UNITARIO", nullable = false, precision = 10, scale = 2)
    private BigDecimal custoUnitario;

    @Column(name = "SUBTOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
