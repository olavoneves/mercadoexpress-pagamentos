package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Controle de e-mails: toda tentativa de envio vira uma linha, com sucesso
 * (ENVIADO) ou falha (FALHA + mensagem de erro do SMTP).
 */
@Entity
@Table(name = "TDS_EMAILS_MERCADO")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "DESTINATARIO", nullable = false, length = 120)
    private String destinatario;

    @Column(name = "ASSUNTO", nullable = false, length = 200)
    private String assunto;

    @Column(name = "COMPRA_ID")
    private Long compraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private StatusEmail status;

    @Column(name = "ERRO", length = 1000)
    private String erro;

    @Column(name = "DATA_ENVIO", nullable = false)
    private LocalDateTime dataEnvio;
}
