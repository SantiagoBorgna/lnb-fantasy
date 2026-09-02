package com.fantasy.lnb.feature.premium;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PremiumService premiumService;

    @PostMapping("/mercadopago")
    public ResponseEntity<String> handleMercadoPagoWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[WEBHOOK MP] Recibido payload: {}", payload.toString());
        
        try {
            String type = payload.containsKey("type") ? payload.get("type").toString() : 
                         (payload.containsKey("topic") ? payload.get("topic").toString() : "");
                         
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            
            if (data != null && data.containsKey("id")) {
                String idStr = data.get("id").toString();
                
                if ("payment".equals(type)) {
                    procesarPago(Long.parseLong(idStr));
                } else if ("subscription_preapproval".equals(type)) {
                    procesarSuscripcion(idStr);
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("[WEBHOOK MP] Error procesando webhook", e);
            return ResponseEntity.ok("Error interno pero ack"); 
        }
    }

    private void procesarPago(Long paymentId) throws MPException, MPApiException {
        PaymentClient client = new PaymentClient();
        Payment payment = client.get(paymentId);
        
        log.info("[WEBHOOK MP] Pago {}: estado={}, external_reference={}", 
                 paymentId, payment.getStatus(), payment.getExternalReference());
                 
        if ("approved".equals(payment.getStatus())) {
            otorgarPremium(payment.getExternalReference());
        }
    }
    
    private void procesarSuscripcion(String preapprovalId) throws MPException, MPApiException {
        PreapprovalClient client = new PreapprovalClient();
        Preapproval preapproval = client.get(preapprovalId);
        
        log.info("[WEBHOOK MP] Suscripción {}: estado={}, external_reference={}", 
                 preapprovalId, preapproval.getStatus(), preapproval.getExternalReference());
                 
        if ("authorized".equals(preapproval.getStatus())) {
            otorgarPremium(preapproval.getExternalReference());
        }
    }
    
    private void otorgarPremium(String externalReference) {
        if (externalReference != null && !externalReference.isEmpty()) {
            try {
                Long usuarioId = Long.parseLong(externalReference);
                premiumService.simularCompra(usuarioId);
                log.info("[WEBHOOK MP] Premium otorgado/renovado al usuario {}", usuarioId);
            } catch (NumberFormatException e) {
                log.error("[WEBHOOK MP] external_reference no válido: {}", externalReference);
            }
        }
    }
}
