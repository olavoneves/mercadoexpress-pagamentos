package br.com.fiap.bancotranquilo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Regras de aprovacao lidas do application.yml (prefixo "banco"). */
@Getter
@Setter
@ConfigurationProperties(prefix = "banco")
public class RegrasBancoProperties {

    private BigDecimal limiteValor = new BigDecimal("5000.00");
    private String sufixoCartaoRecusado = "0000";
    private List<String> cpfsBloqueados = new ArrayList<>();
    private long atrasoMinimoMs = 500;
    private long atrasoMaximoMs = 1500;
}
