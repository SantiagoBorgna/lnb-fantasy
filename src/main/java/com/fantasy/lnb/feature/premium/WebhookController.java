package com.fantasy.lnb.feature.premium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PremiumService premiumService;

    @Value("${mercadopago.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/mercadopago")
    public ResponseEntity<String> handleMercadoPagoWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam) {

        if (!firmaValida(xSignature, xRequestId, dataIdParam)) {
            log.warn("[WEBHOOK MP] Firma inválida o ausente (posible intento de falsificación). x-request-id={}, data.id={}",
                     xRequestId, dataIdParam);
            return ResponseEntity.status(401).body("Firma inválida");
        }

        log.info("[WEBHOOK MP] Recibido payload: {}", payload.toString());

        try {
            String type = payload.containsKey("type") ? payload.get("type").toString() : 
                         (payload.containsKey("topic") ? payload.get("topic").toString() : "");
                         
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            
            if (data != null && data.containsKey("id")) {
                String idStr = data.get("id").toString();
                
                if ("payment".equals(type)) {
                    procesarPago(Long.parseLong(idStr), null);
                } else if ("subscription_preapproval".equals(type)) {
                    procesarSuscripcion(idStr);
                } else if ("subscription_authorized_payment".equals(type)) {
                    procesarCobroMensual(idStr);
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("[WEBHOOK MP] Error procesando webhook", e);
            return ResponseEntity.ok("Error interno pero ack"); 
        }
    }

    /**
     * Cada pago aprobado acredita el mes que va desde su fecha de aprobación. Los avisos repetidos del mismo
     * pago (MP los reenvía cuando el pago cambia, por ejemplo al liberar la plata) no suman nada.
     * referenciaSuscripcion se usa si el pago no trae external_reference (cobros de una suscripción).
     */
    private void procesarPago(Long paymentId, String referenciaSuscripcion) throws MPException, MPApiException {
        PaymentClient client = new PaymentClient();
        Payment payment = client.get(paymentId);

        log.info("[WEBHOOK MP] Pago {}: estado={}, external_reference={}, aprobado={}",
                 paymentId, payment.getStatus(), payment.getExternalReference(), payment.getDateApproved());

        if ("approved".equals(payment.getStatus()) && payment.getDateApproved() != null) {
            String referencia = payment.getExternalReference() != null && !payment.getExternalReference().isBlank()
                    ? payment.getExternalReference() : referenciaSuscripcion;
            acreditar(referencia, payment.getDateApproved(), "pago " + paymentId);
        }
    }

    /**
     * La suscripción sólo da Premium al activarse, y por el mes que arranca en su creación.
     * Los meses siguientes los acredita cada cobro aprobado: MP manda este aviso con estado "authorized"
     * cada vez que la suscripción cambia (incluso si el cobro del mes falla), así que no sirve para renovar.
     */
    private void procesarSuscripcion(String preapprovalId) throws MPException, MPApiException {
        PreapprovalClient client = new PreapprovalClient();
        Preapproval preapproval = client.get(preapprovalId);

        log.info("[WEBHOOK MP] Suscripción {}: estado={}, external_reference={}, creada={}",
                 preapprovalId, preapproval.getStatus(), preapproval.getExternalReference(), preapproval.getDateCreated());

        if ("authorized".equals(preapproval.getStatus()) && preapproval.getDateCreated() != null) {
            acreditar(preapproval.getExternalReference(), preapproval.getDateCreated(), "suscripción " + preapprovalId);
        }
    }

    /** Cobro mensual de una suscripción: se acredita sólo si el pago asociado quedó aprobado. */
    private void procesarCobroMensual(String authorizedPaymentId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mercadopago.com/authorized_payments/" + authorizedPaymentId))
                .header("Authorization", "Bearer " + MercadoPagoConfig.getAccessToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("[WEBHOOK MP] Cobro mensual {}: MP respondió {}", authorizedPaymentId, res.statusCode());
            return;
        }
        JsonNode cobro = JSON.readTree(res.body());
        JsonNode pago = cobro.path("payment");
        log.info("[WEBHOOK MP] Cobro mensual {}: estado={}, pago={} ({}), suscripción={}",
                 authorizedPaymentId, cobro.path("status").asText(), pago.path("id").asText(),
                 pago.path("status").asText(), cobro.path("preapproval_id").asText());

        if (!"approved".equals(pago.path("status").asText()) || !pago.path("id").canConvertToLong()) {
            return;
        }
        String referencia = cobro.path("external_reference").asText("");
        if (referencia.isBlank() && !cobro.path("preapproval_id").asText("").isBlank()) {
            referencia = new PreapprovalClient().get(cobro.path("preapproval_id").asText()).getExternalReference();
        }
        procesarPago(pago.path("id").asLong(), referencia);
    }

    private void acreditar(String externalReference, OffsetDateTime fechaPago, String origen) {
        if (externalReference != null && !externalReference.isEmpty()) {
            try {
                Long usuarioId = Long.parseLong(externalReference);
                premiumService.acreditarPago(usuarioId,
                        fechaPago.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), origen);
            } catch (NumberFormatException e) {
                log.error("[WEBHOOK MP] external_reference no válido: {}", externalReference);
            }
        }
    }

    /**
     * Valida la firma HMAC-SHA256 que Mercado Pago envía en el header x-signature,
     * siguiendo su esquema: manifest = "id:{data.id};request-id:{x-request-id};ts:{ts};"
     * firmado con el webhook secret. Evita que alguien falsifique un webhook de "pago aprobado".
     */
    private boolean firmaValida(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || xRequestId == null || dataId == null) {
            return false;
        }

        String ts = null;
        String hashRecibido = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                if ("ts".equals(key)) {
                    ts = value;
                } else if ("v1".equals(key)) {
                    hashRecibido = value;
                }
            }
        }
        if (ts == null || hashRecibido == null) {
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String hashCalculado = bytesToHex(hmacBytes);
            return MessageDigest.isEqual(
                    hashCalculado.getBytes(StandardCharsets.UTF_8),
                    hashRecibido.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[WEBHOOK MP] Error calculando firma HMAC", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
