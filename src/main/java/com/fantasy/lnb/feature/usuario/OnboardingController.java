package com.fantasy.lnb.feature.usuario;

import com.fantasy.lnb.feature.usuario.dto.CompletarPerfilRequest;
import com.fantasy.lnb.feature.usuario.dto.UsuarioPerfilDto;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final UsuarioResolver usuarioResolver;
    private final EquipoRealRepository equipoRealRepo;
    private final UsuarioService usuarioService;

    /**
     * GET /api/onboarding/perfil
     * Devuelve el estado completo del usuario.
     * El frontend lo llama al iniciar para saber a qué pantalla redirigir.
     */
    @GetMapping("/perfil")
    public ResponseEntity<UsuarioPerfilDto> obtenerPerfil(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long id = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.obtenerPerfil(id));
    }

    /**
     * POST /api/onboarding/completar-perfil
     * Paso 1: el usuario elige equipo favorito y nombre de equipo virtual.
     */
    @PostMapping("/completar-perfil")
    public ResponseEntity<UsuarioPerfilDto> completarPerfil(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CompletarPerfilRequest request) {
        Long id = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.completarPerfil(id, request));
    }

    /**
     * POST /api/onboarding/activar
     * Paso 2: se llama automáticamente al guardar el primer plantel.
     * Marca al usuario como ACTIVO y desbloquea toda la app.
     */
    @PostMapping("/activar")
    public ResponseEntity<Void> activar(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long id = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        onboardingService.marcarActivo(id);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/onboarding/ayuda/{pagina}
     * Marca una pantalla de ayuda como vista para el usuario.
     */
    @PostMapping("/ayuda/{pagina}")
    public ResponseEntity<Void> marcarAyudaVista(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String pagina) {
        Long id = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        usuarioService.marcarAyudaVista(id, pagina);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/onboarding/equipos
     * Lista de equipos reales para el selector del onboarding.
     * Público — el usuario no está autenticado aún cuando elige su favorito.
     * En realidad sí tiene token, pero lo exponemos como público por simplicidad.
     */
    @GetMapping("/equipos")
    public ResponseEntity<List<EquipoReal>> listarEquipos() {
        return ResponseEntity.ok(equipoRealRepo.findAll());
    }
}