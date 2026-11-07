package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.config.LigaConfig;
import com.fantasy.lnb.feature.usuario.RolUsuario;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Avisos operativos que solo tienen que ver los admins. El título lleva el nombre
 * de la liga porque cada liga corre su propio backend y los avisos llegan juntos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvisoAdminService {

    private final UsuarioRepository usuarioRepo;
    private final PushNotificationService pushService;
    private final LigaConfig liga;

    public void avisar(String titulo, String mensaje) {
        log.warn("[AVISO-ADMIN] {}: {}", titulo, mensaje);

        for (Usuario admin : usuarioRepo.findByRol(RolUsuario.ADMIN)) {
            try {
                pushService.enviarNotificacionAUsuario(admin, liga.getNombre() + ": " + titulo, mensaje, "/admin", "DEFAULT");
            } catch (Exception e) {
                log.error("[AVISO-ADMIN] No se pudo avisar al admin {}: {}", admin.getId(), e.getMessage());
            }
        }
    }
}
