package br.com.fiap.pagamentointegracao.banco;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP do Banco Tranquilo (RestClient).
 *
 * <p>Nao trata erro de proposito: se o banco estiver fora do ar ou devolver
 * 5xx, a excecao sobe ate o listener e o retry do Spring AMQP entra em acao.</p>
 */
@Slf4j
@Component
public class BancoTranquiloClient {

    private final RestClient restClient;

    public BancoTranquiloClient(RestClient.Builder builder,
                                @Value("${banco-tranquilo.url}") String url,
                                @Value("${banco-tranquilo.connect-timeout:2s}") Duration connectTimeout,
                                @Value("${banco-tranquilo.read-timeout:5s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(connectTimeout);
        fabrica.setReadTimeout(readTimeout);
        this.restClient = builder
                .baseUrl(url)
                .requestFactory(fabrica)
                .build();
        log.info("Banco Tranquilo em {}", url);
    }

    public BancoPagamentoResponse autorizar(BancoPagamentoRequest pedido) {
        return restClient.post()
                .uri("/api/pagamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(pedido)
                .retrieve()
                .body(BancoPagamentoResponse.class);
    }
}
