package com.fantasy.lnb.feature.arrepentimiento;

import com.fantasy.lnb.feature.arrepentimiento.dto.SolicitudArrepentimientoRequest;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;
import com.fantasy.lnb.feature.usuario.RolUsuario;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArrepentimientoService {

    static final int MAX_MOTIVO = 500;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SolicitudArrepentimientoRepository solicitudRepo;
    private final UsuarioRepository usuarioRepo;
    private final PushNotificationService pushNotificationService;

    /** Registra el pedido y devuelve la solicitud guardada (con su código). */
    @Transactional
    public SolicitudArrepentimiento registrar(SolicitudArrepentimientoRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Ingresá un email válido");
        }
        if (request.getFechaContratacion() != null && request.getFechaContratacion().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de contratación no puede ser futura");
        }
        String motivo = request.getMotivo() == null ? null : request.getMotivo().trim();
        if (motivo != null && motivo.length() > MAX_MOTIVO) {
            throw new IllegalArgumentException("El motivo puede tener hasta " + MAX_MOTIVO + " caracteres");
        }

        Long usuarioId = usuarioRepo.findByEmail(email).map(Usuario::getId).orElse(null);
        SolicitudArrepentimiento guardada = solicitudRepo.save(SolicitudArrepentimiento.builder()
                .email(email)
                .fechaContratacion(request.getFechaContratacion())
                .motivo(motivo == null || motivo.isEmpty() ? null : motivo)
                .usuarioId(usuarioId)
                .build());

        log.info("[ARREPENTIMIENTO] Nuevo pedido {} de {}", guardada.getCodigo(), email);
        avisarAdmins(guardada);
        return guardada;
    }

    @Transactional(readOnly = true)
    public List<SolicitudArrepentimiento> listar() {
        return solicitudRepo.findAllByOrderByCreadoEnDesc();
    }

    @Transactional
    public SolicitudArrepentimiento marcarResuelta(Long id) {
        SolicitudArrepentimiento s = solicitudRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (s.getResueltoEn() == null) {
            s.setResueltoEn(LocalDateTime.now());
            solicitudRepo.save(s);
        }
        return s;
    }

    // El pedido hay que atenderlo rápido: se avisa a los admins (si falla, el pedido igual queda guardado)
    private void avisarAdmins(SolicitudArrepentimiento s) {
        try {
            for (Usuario admin : usuarioRepo.findByRol(RolUsuario.ADMIN)) {
                pushNotificationService.enviarNotificacionAUsuario(admin,
                        "Botón de arrepentimiento",
                        s.getCodigo() + " · " + s.getEmail(),
                        "/admin", "DEFAULT");
            }
        } catch (Exception e) {
            log.warn("[ARREPENTIMIENTO] No se pudo avisar a los admins de {}: {}", s.getCodigo(), e.getMessage());
        }
    }
}
