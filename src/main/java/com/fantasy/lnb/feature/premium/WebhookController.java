package com.fantasy.lnb.feature.premium;

import com.fasterxml.jackson.databind.JsonNode;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PremiumService premiumService;

    @PostMapping("/mercadopago")
    public ResponseEntity<String> handleMercadoPagoWebhook(@RequestBody JsonNode payload) {
        log.info("[WEBHOOK MP] Recibido payload: {}", payload.toString());
        
        try {
            if (payload.has("type") && "payment".equals(payload.get("type").asText())) {
                JsonNode data = payload.get("data");
                if (data != null && data.has("id")) {
                    Long paymentId = data.get("id").asLong();
                    procesarPago(paymentId);
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("[WEBHOOK MP] Error procesando webhook", e);
            // MP espera un 200 OK igual para no reintentar infinitamente si no es un error de red
            return ResponseEntity.ok("Error interno pero ack"); 
        }
    }

    private void procesarPago(Long paymentId) throws MPException, MPApiException {
        PaymentClient client = new PaymentClient();
        Payment payment = client.get(paymentId);
        
        log.info("[WEBHOOK MP] Pago {}: estado={}, external_reference={}", 
                 paymentId, payment.getStatus(), payment.getExternalReference());
                 
        if ("approved".equals(payment.getStatus())) {
            String externalReference = payment.getExternalReference();
            if (externalReference != null && !externalReference.isEmpty()) {
                try {
                    Long usuarioId = Long.parseLong(externalReference);
                    premiumService.simularCompra(usuarioId); // Reutilizamos el método que otorga 1 mes de premium
                    log.info("[WEBHOOK MP] Suscripción otorgada/renovada al usuario {}", usuarioId);
                } catch (NumberFormatException e) {
                    log.error("[WEBHOOK MP] external_reference no es un ID numérico válido: {}", externalReference);
                }
            }
        }
    }
}
