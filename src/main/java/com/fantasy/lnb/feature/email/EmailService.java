package com.fantasy.lnb.feature.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envío de mails con Resend (https://resend.com). Sin RESEND_API_KEY configurada no manda
 * nada (solo lo deja en el log), así los ambientes sin la clave siguen funcionando.
 * El envío es asíncrono y un error nunca corta lo que lo llamó.
 */
@Slf4j
@Service
public class EmailService {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String remitente;
    private final String emailAdmin;

    public EmailService(
            @Value("${app.email.resend-api-key:}") String apiKey,
            @Value("${app.email.remitente:Sexto Hombre Fantasy <avisos@sextohombrefantasy.com.ar>}") String remitente,
            @Value("${app.email.admin:sextohombrefantasy@gmail.com}") String emailAdmin) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente;
        this.emailAdmin = emailAdmin;
    }

    public boolean estaConfigurado() {
        return !apiKey.isBlank();
    }

    public String getEmailAdmin() {
        return emailAdmin;
    }

    /** Manda un mail en HTML. replyTo es opcional. */
    public void enviar(String para, String asunto, String html, String replyTo) {
        if (!estaConfigurado()) {
            log.info("[EMAIL] Sin RESEND_API_KEY: no se manda '{}' a {}", asunto, para);
            return;
        }
        try {
            Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of(
                    "from", remitente,
                    "to", List.of(para),
                    "subject", asunto,
                    "html", html));
            if (replyTo != null && !replyTo.isBlank()) cuerpo.put("reply_to", replyTo);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo)))
                    .build();

            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() >= 300) {
                            log.error("[EMAIL] Resend respondió {} al mandar '{}' a {}: {}", res.statusCode(), asunto, para, res.body());
                        } else {
                            log.info("[EMAIL] Enviado '{}' a {}", asunto, para);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("[EMAIL] No se pudo mandar '{}' a {}: {}", asunto, para, e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("[EMAIL] No se pudo armar el mail '{}' para {}: {}", asunto, para, e.getMessage());
        }
    }

    /** Escapa texto que escribió un usuario antes de meterlo en el HTML del mail. */
    public static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
