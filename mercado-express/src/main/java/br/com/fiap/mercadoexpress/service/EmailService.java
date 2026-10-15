package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.model.Compra;
import br.com.fiap.mercadoexpress.model.EmailEnviado;
import br.com.fiap.mercadoexpress.model.StatusCompra;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.repository.CompraRepository;
import br.com.fiap.mercadoexpress.repository.EmailEnviadoRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Envia o e-mail com o resultado da compra (template HTML Thymeleaf) e grava
 * TODA tentativa em TDS_EMAILS_MERCADO.
 *
 * <p>Falha de SMTP nao derruba o fluxo: a compra ja foi atualizada em outra
 * transacao; aqui a excecao vira uma linha com status FALHA e a mensagem de
 * erro, e o consumidor da fila confirma a mensagem normalmente.</p>
 */
@Slf4j
@Service
public class EmailService {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templates;
    private final CompraRepository compras;
    private final EmailEnviadoRepository emails;
    private final String remetente;
    private final String urlPublica;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templates,
                        CompraRepository compras, EmailEnviadoRepository emails,
                        @Value("${mercado.email-remetente}") String remetente,
                        @Value("${mercado.url-publica}") String urlPublica) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.compras = compras;
        this.emails = emails;
        this.remetente = remetente;
        this.urlPublica = urlPublica;
    }

    public EmailEnviado enviarResultado(Long compraId) {
        Compra compra = compras.buscarCompleta(compraId).orElseThrow();
        String assunto = assuntoPara(compra);

        EmailEnviado registro = EmailEnviado.builder()
                .destinatario(compra.getClienteEmail())
                .assunto(assunto)
                .compraId(compra.getId())
                .build();
        try {
            Context contexto = new Context(PT_BR);
            contexto.setVariable("compra", compra);
            contexto.setVariable("linkPedido", urlPublica + "/pedidos/" + compra.getId());
            String html = templates.process("email/resultado-compra", contexto);

            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, false, "UTF-8");
            ajudante.setFrom(remetente);
            ajudante.setTo(compra.getClienteEmail());
            ajudante.setSubject(assunto);
            ajudante.setText(html, true);
            mailSender.send(mensagem);

            registro.setStatus(StatusEmail.ENVIADO);
            log.info("Compra {}: e-mail enviado para {}", compra.getId(), compra.getClienteEmail());
        } catch (Exception e) {
            registro.setStatus(StatusEmail.FALHA);
            registro.setErro(resumir(e));
            log.warn("Compra {}: falha ao enviar e-mail para {}: {}", compra.getId(), compra.getClienteEmail(),
                    registro.getErro());
        }
        registro.setDataEnvio(LocalDateTime.now());
        return emails.save(registro);
    }

    static String assuntoPara(Compra compra) {
        return compra.getStatus() == StatusCompra.APROVADO
                ? "Pedido #" + compra.getId() + " aprovado | Mercado Express"
                : "Pedido #" + compra.getId() + " não aprovado | Mercado Express";
    }

    private static String resumir(Exception e) {
        StringBuilder texto = new StringBuilder(e.getClass().getSimpleName() + ": " + e.getMessage());
        Throwable causa = e.getCause();
        if (causa != null && causa != e) {
            texto.append(" | causa: ").append(causa.getMessage());
        }
        return texto.length() > 1000 ? texto.substring(0, 1000) : texto.toString();
    }
}
