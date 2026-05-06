package com.fantasy.lnb.feature.usuario;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.usuario.dto.CompletarPerfilRequest;
import com.fantasy.lnb.feature.usuario.dto.UsuarioPerfilDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

        private final UsuarioRepository usuarioRepo;
        private final EquipoVirtualRepository equipoVirtualRepo;
        private final EquipoRealRepository equipoRealRepo;

        // ── Paso 1: completar perfil ─────────────────────────────────────────────
        @Transactional
        public UsuarioPerfilDto completarPerfil(
                        Long usuarioId,
                        CompletarPerfilRequest request) {

                Usuario usuario = usuarioRepo.findById(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Usuario no encontrado: " + usuarioId));

                EquipoReal equipoFavorito = equipoRealRepo
                                .findById(request.getEquipoFavoritoId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Equipo no encontrado: " + request.getEquipoFavoritoId()));

                // Actualizar equipo favorito
                usuario.setEquipoFavorito(equipoFavorito);
                usuario.setEstadoOnboarding(EstadoOnboarding.PERFIL_COMPLETO);
                usuarioRepo.save(usuario);

                // Actualizar nombre del equipo virtual
                EquipoVirtual equipo = equipoVirtualRepo
                                .findByUsuario_Id(usuarioId)
                                .orElseGet(() -> {
                                        // Crear equipo virtual si no existe (usuario muy nuevo)
                                        EquipoVirtual nuevo = EquipoVirtual.builder()
                                                        .usuario(usuario)
                                                        .nombre(request.getNombreEquipoVirtual())
                                                        .presupuestoActual(100.0)
                                                        .puntajeGlobal(0.0)
                                                        .build();
                                        return equipoVirtualRepo.save(nuevo);
                                });

                equipo.setNombre(request.getNombreEquipoVirtual());
                equipoVirtualRepo.save(equipo);

                log.info("[ONBOARDING] Usuario {} completó perfil. Equipo: '{}' | Favorito: {}",
                                usuario.getEmail(),
                                request.getNombreEquipoVirtual(),
                                equipoFavorito.getNombre());

                return toDto(usuario, equipo);
        }

        // ── Paso 2: marcar onboarding completo (después de guardar el primer plantel)
        @Transactional
        public void marcarActivo(Long usuarioId) {
                usuarioRepo.findById(usuarioId).ifPresent(usuario -> {
                        if (usuario.getEstadoOnboarding() != EstadoOnboarding.ACTIVO) {
                                usuario.setEstadoOnboarding(EstadoOnboarding.ACTIVO);
                                ;
                                usuarioRepo.save(usuario);
                                log.info("[ONBOARDING] Usuario {} → ACTIVO", usuario.getEmail());
                        }
                });
        }

        // ── Consulta del perfil completo ─────────────────────────────────────────
        @Transactional(readOnly = true)
        public UsuarioPerfilDto obtenerPerfil(Long usuarioId) {
                Usuario usuario = usuarioRepo.findById(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Usuario no encontrado: " + usuarioId));

                EquipoVirtual equipo = equipoVirtualRepo
                                .findByUsuario_Id(usuarioId)
                                .orElse(null);

                return toDto(usuario, equipo);
        }

        // ── Mapper ───────────────────────────────────────────────────────────────
        private UsuarioPerfilDto toDto(Usuario u, EquipoVirtual ev) {
                var builder = UsuarioPerfilDto.builder()
                                .id(u.getId())
                                .email(u.getEmail())
                                .nombreDisplay(u.getNombreDisplay())
                                .avatarUrl(u.getAvatarUrl())
                                .estadoOnboarding(u.getEstadoOnboarding());

                if (u.getEquipoFavorito() != null) {
                        builder
                                        .equipoFavoritoId(u.getEquipoFavorito().getId())
                                        .equipoFavoritoNombre(u.getEquipoFavorito().getNombre())
                                        .equipoFavoritoSigla(u.getEquipoFavorito().getSigla())
                                        .colorPrincipal(u.getEquipoFavorito().getColorPrincipal())
                                        .colorSecundario(u.getEquipoFavorito().getColorSecundario())
                                        .modeloCamiseta(u.getEquipoFavorito().getModeloCamiseta());
                }

                if (ev != null) {
                        builder
                                        .equipoVirtualId(ev.getId())
                                        .nombreEquipoVirtual(ev.getNombre())
                                        .presupuestoActual(ev.getPresupuestoActual())
                                        .puntajeGlobal(ev.getPuntajeGlobal());
                }

                return builder.build();
        }
}