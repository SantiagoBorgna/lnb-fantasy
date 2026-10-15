package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.notificaciones.dto.EnviarNotificacionRequest;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/notificaciones")
@RequiredArgsConstructor
public class AdminNotificacionesController {

    private final AdminNotificacionesService adminNotificacionesService;
    private final UsuarioResolver usuarioResolver;

    /**
     * POST /api/admin/notificaciones/enviar
     * Body: { titulo, mensaje, url, soloPrueba }
     * Devuelve a cuántos dispositivos se manda. El envío a todos sigue en segundo plano.
     */
    @PostMapping("/enviar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> enviar(
            @RequestBody EnviarNotificacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        var admin = usuarioResolver.resolverDesdeEmail(userDetails.getUsername());
        long dispositivos = adminNotificacionesService.enviar(request, admin);
        return ResponseEntity.ok(Map.of(
                "dispositivos", dispositivos,
                "soloPrueba", request.isSoloPrueba()));
    }
}
