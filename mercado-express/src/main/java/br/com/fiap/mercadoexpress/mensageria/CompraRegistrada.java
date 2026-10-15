package br.com.fiap.mercadoexpress.mensageria;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * Evento interno (ApplicationEvent do Spring, nao vai para o RabbitMQ): avisa
 * que uma compra PENDENTE foi gravada. Carrega o numero do cartao em memoria
 * ate a publicacao, ja que ele nunca e salvo no banco.
 */
@Getter
@AllArgsConstructor
public class CompraRegistrada {

    private final Long compraId;

    @ToString.Exclude
    private final String numeroCartao;

    private final String titularCartao;
}
