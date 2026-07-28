package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.Map;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final SuscripcionPushRepository suscripcionRepo;
    // Inicialización explícita para evitar que Spring Boot intente autowirearlo
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PushService pushService;

    @Value("${app.webpush.public-key}")
    private String publicKey;

    @Value("${app.webpush.private-key}")
    private String privateKey;

    @Value("${app.webpush.subject}")
    private String subject;

    @PostConstruct
    public void init() {
        try {
            // Web-push necesita el proveedor criptográfico BouncyCastle
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            // Inicializamos el motor de envíos con nuestras llaves VAPID
            this.pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            log.error("[PUSH] Error inicializando PushService VAPID: {}", e.getMessage());
        }
    }

    @Transactional
    public void guardarSuscripcion(Usuario usuario, String endpoint, String p256dh, String auth) {
        // Si el dispositivo ya estaba registrado con ese mismo endpoint, evitamos
        // duplicados
        suscripcionRepo.findByEndpoint(endpoint).ifPresentOrElse(
                existente -> {
                    existente.setUsuario(usuario);
                    suscripcionRepo.save(existente);
                },
                () -> {
                    SuscripcionPush nueva = SuscripcionPush.builder()
                            .endpoint(endpoint)
                            .p256dh(p256dh)
                            .auth(auth)
                            .usuario(usuario)
                            .build();
                    suscripcionRepo.save(nueva);
                    log.info("[PUSH] Nueva suscripción guardada para el usuario: {}", usuario.getEmail());
                });
    }

    /**
     * Envía una notificación push asíncrona a un dispositivo específico.
     */
    public void enviarNotificacion(SuscripcionPush suscripcion, String titulo, String mensaje) {
        enviarNotificacion(suscripcion, titulo, mensaje, "/", "DEFAULT");
    }

    public void enviarNotificacion(SuscripcionPush suscripcion, String titulo, String mensaje, String url, String type) {
        try {
            // Mapeamos los datos de nuestra BD al formato que pide la librería
            Subscription sub = new Subscription(
                    suscripcion.getEndpoint(),
                    new Subscription.Keys(suscripcion.getP256dh(), suscripcion.getAuth()));

            // Estructura estándar que espera el Service Worker en el frontend
            Map<String, String> payload = Map.of(
                    "title", titulo,
                    "body", mensaje,
                    "url", url,
                    "type", type);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Construimos la notificación
            Notification notification = new Notification(sub, jsonPayload);

            // Enviamos el mensaje al servidor intermedio (Google/Apple/etc)
            var response = pushService.send(notification);

            // Si el servidor nos devuelve 410 Gone, significa que el usuario desinstaló la
            // app
            // o revocó los permisos desde los ajustes de su celular. Borramos la
            // suscripción inválida.
            if (response.getStatusLine().getStatusCode() == 410) {
                log.info("[PUSH] Suscripción expirada o revocada (410). Eliminando endpoint.");
                suscripcionRepo.delete(suscripcion);
            }

        } catch (Exception e) {
            log.error("[PUSH] Falló el envío al endpoint del usuario {}: {}",
                    suscripcion.getUsuario().getEmail(), e.getMessage());
        }
    }

    /**
     * Envía una notificación push a todos los usuarios suscritos.
     */
    public void enviarNotificacionMasiva(String titulo, String mensaje) {
        List<SuscripcionPush> suscripciones = suscripcionRepo.findAll();
        log.info("[PUSH] Enviando notificación masiva a {} dispositivos", suscripciones.size());
        
        for (SuscripcionPush sub : suscripciones) {
            // Enviar de forma asincrónica usando un hilo o directamente (lo ideal sería usar @Async)
            // Para simplicidad en este CronJob lo mandamos síncrono o se podría mandar en un Executor
            // Pero como son notificaciones, vamos a mandarlas iterando.
            enviarNotificacion(sub, titulo, mensaje);
        }
    }

    /**
     * Envía una notificación a un usuario específico en todas sus sesiones/dispositivos.
     */
    public void enviarNotificacionAUsuario(Usuario usuario, String titulo, String mensaje) {
        enviarNotificacionAUsuario(usuario, titulo, mensaje, "/", "DEFAULT");
    }

    public void enviarNotificacionAUsuario(Usuario usuario, String titulo, String mensaje, String url, String type) {
        if (usuario == null) return;
        List<SuscripcionPush> suscripciones = suscripcionRepo.findByUsuario_Id(usuario.getId());
        for (SuscripcionPush sub : suscripciones) {
            enviarNotificacion(sub, titulo, mensaje, url, type);
        }
    }
}