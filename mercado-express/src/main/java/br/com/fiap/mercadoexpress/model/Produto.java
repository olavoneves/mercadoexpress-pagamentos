package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Produto da vitrine. Mesma entidade da Parte I (nome, tipo, setor, tamanho,
 * descricao, estoque, ativo, data de cadastro), com duas mudancas para a
 * gerencia: o preco virou PRECO_VENDA e entrou PRECO_CUSTO, para o dashboard
 * calcular lucro, e ESTOQUE_MINIMO dispara o alerta de estoque baixo.
 */
@Entity
@Table(name = "TDS_PRODUTOS_MERCADO")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "NOME", nullable = false, length = 100)
    private String nome;

    @Column(name = "TIPO", nullable = false, length = 50)
    private String tipo;

    @Column(name = "SETOR", nullable = false, length = 50)
    private String setor;

    @Column(name = "TAMANHO", length = 30)
    private String tamanho;

    @Column(name = "PRECO_VENDA", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "PRECO_CUSTO", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "DESCRICAO", length = 500)
    private String descricao;

    @Column(name = "ESTOQUE", nullable = false)
    private Integer estoque;

    @Column(name = "ESTOQUE_MINIMO", nullable = false)
    private Integer estoqueMinimo;

    @Column(name = "ATIVO", nullable = false)
    private Boolean ativo;

    @Column(name = "DATA_CADASTRO", nullable = false)
    private LocalDate dataCadastro;

    @PrePersist
    private void aoCadastrar() {
        if (dataCadastro == null) {
            dataCadastro = LocalDate.now();
        }
        if (ativo == null) {
            ativo = Boolean.TRUE;
        }
        if (estoqueMinimo == null) {
            estoqueMinimo = 10;
        }
    }

    public boolean isEsgotado() {
        return estoque == null || estoque <= 0;
    }

    public boolean isEstoqueBaixo() {
        return estoque != null && estoqueMinimo != null && estoque <= estoqueMinimo;
    }
}
