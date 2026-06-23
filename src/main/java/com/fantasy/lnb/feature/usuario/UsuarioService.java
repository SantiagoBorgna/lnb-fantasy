package com.fantasy.lnb.feature.usuario;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.usuario.dto.ActualizarPerfilRequest;
import com.fantasy.lnb.feature.usuario.dto.UsuarioPerfilDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepo;
    private final EquipoVirtualRepository equipoVirtualRepo;
    private final EquipoRealRepository equipoRealRepo;
    private final OnboardingService onboardingService;

    @Transactional
    public UsuarioPerfilDto actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + usuarioId));

        EquipoReal equipoFavorito = equipoRealRepo.findById(request.getEquipoFavoritoId())
                .orElseThrow(() -> new IllegalArgumentException("Equipo no encontrado: " + request.getEquipoFavoritoId()));

        EquipoVirtual equipoVirtual = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Equipo virtual no encontrado para usuario: " + usuarioId));

        // Actualizar datos
        usuario.setNombreDisplay(request.getNombreDisplay());
        usuario.setEquipoFavorito(equipoFavorito);
        usuarioRepo.save(usuario);

        equipoVirtual.setNombre(request.getNombreEquipo());
        equipoVirtualRepo.save(equipoVirtual);

        log.info("[PERFIL] Usuario {} actualizó su perfil. Nuevo display: {}, Equipo: {}, Favorito: {}", 
            usuario.getEmail(), request.getNombreDisplay(), request.getNombreEquipo(), equipoFavorito.getNombre());

        // Reutilizamos el mapper del onboarding (o podríamos moverlo a un utilitario)
        return onboardingService.obtenerPerfil(usuarioId);
    }
}
