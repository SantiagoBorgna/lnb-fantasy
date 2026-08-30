package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preapproval.Preapproval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final UsuarioRepository usuarioRepository;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public String createSubscriptionPreference(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        try {
            PreapprovalClient client = new PreapprovalClient();
            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .reason("Suscripción Premium - 6to Hombre")
                    .externalReference(usuarioId.toString())
                    .payerEmail(usuario.getEmail())
                    .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                            .frequency(1)
                            .frequencyType("months")
                            .transactionAmount(new BigDecimal("5000"))
                            .currencyId("ARS")
                            .build())
                    .backUrl(frontendUrl + "/premium/success")
                    .status("pending")
                    .build();

            Preapproval preapproval = client.create(request);
            
            log.info("[MP-PREAPPROVAL] Creado init_point para el usuario {}: {}", usuario.getEmail(), preapproval.getInitPoint());
            
            return preapproval.getInitPoint();

        } catch (MPException | MPApiException e) {
            log.error("[MP-PREAPPROVAL] Error creando la suscripción en Mercado Pago", e);
            throw new RuntimeException("Error al comunicarse con Mercado Pago", e);
        }
    }
}
