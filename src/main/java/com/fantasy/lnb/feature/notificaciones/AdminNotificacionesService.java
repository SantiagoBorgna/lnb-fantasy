package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.notificaciones.dto.EnviarNotificacionRequest;
import com.fantasy.lnb.feature.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Notificaciones con texto libre que manda el admin (anuncios, avisos).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificacionesService {

    static final int MAX_TITULO = 60;
    static final int MAX_MENSAJE = 200;
    static final String TIPO = "ANUNCIO";

    private final PushNotificationService pushService;
    private final SuscripcionPushRepository suscripcionRepo;

    // El envío masivo va en segundo plano: son cientos de envíos de a uno y el panel no tiene que esperar.
    // Se puede reemplazar en tests por un executor que corre en el mismo hilo.
    private Executor executor = ForkJoinPool.commonPool();

    void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * @return cantidad de dispositivos a los que se manda
     */
    public long enviar(EnviarNotificacionRequest req, Usuario admin) {
        String titulo = validarTexto(req.getTitulo(), "título", MAX_TITULO);
        String mensaje = validarTexto(req.getMensaje(), "mensaje", MAX_MENSAJE);
        String url = normalizarUrl(req.getUrl());

        if (req.isSoloPrueba()) {
            long dispositivos = suscripcionRepo.findByUsuario_Id(admin.getId()).size();
            if (dispositivos == 0) {
                throw new IllegalArgumentException(
                        "No tenés las notificaciones activadas en ningún dispositivo. Activalas en la app y volvé a probar.");
            }
            pushService.enviarNotificacionAUsuario(admin, titulo, mensaje, url, TIPO);
            log.info("[PUSH-ADMIN] Prueba enviada por {} a {} dispositivo(s)", admin.getEmail(), dispositivos);
            return dispositivos;
        }

        long dispositivos = suscripcionRepo.count();
        log.info("[PUSH-ADMIN] {} envía una notificación a {} dispositivo(s): '{}'", admin.getEmail(), dispositivos, titulo);
        CompletableFuture.runAsync(() -> {
            try {
                pushService.enviarNotificacionMasiva(titulo, mensaje, url, TIPO);
            } catch (Exception e) {
                log.error("[PUSH-ADMIN] Falló el envío masivo: {}", e.getMessage(), e);
            }
        }, executor);
        return dispositivos;
    }

    private static String validarTexto(String texto, String campo, int max) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El " + campo + " no puede estar vacío.");
        }
        if (limpio.length() > max) {
            throw new IllegalArgumentException("El " + campo + " puede tener hasta " + max + " caracteres.");
        }
        return limpio;
    }

    // Solo rutas internas de la app: evita que una notificación lleve a otro sitio
    static String normalizarUrl(String url) {
        if (url == null || url.isBlank()) return "/";
        String limpia = url.trim();
        if (!limpia.startsWith("/") || limpia.startsWith("//")) {
            throw new IllegalArgumentException("El destino tiene que ser una pantalla de la app (ej. /torneos).");
        }
        return limpia;
    }
}
