package br.com.fiap.mercadoexpress.model;

/** Por onde a compra entrou: carrinho da loja (Thymeleaf) ou API REST (Postman, curl). */
public enum OrigemCompra {
    LOJA,
    API
}
