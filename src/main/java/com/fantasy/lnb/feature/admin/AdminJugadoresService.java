package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJugadorDto;
import com.fantasy.lnb.feature.admin.dto.AdminJugadorUpdateRequestDto;
import com.fantasy.lnb.feature.admin.dto.EquipoRealBasicoDto;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminJugadoresService {

    private final JugadorRealRepository jugadorRepo;
    private final EquipoRealRepository equipoRepo;

    @Transactional(readOnly = true)
    public List<AdminJugadorDto> getAllJugadores() {
        return jugadorRepo.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EquipoRealBasicoDto> getAllEquipos() {
        return equipoRepo.findAll().stream().map(e -> EquipoRealBasicoDto.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .sigla(e.getSigla())
                .build()).collect(Collectors.toList());
    }

    @Transactional
    public void updateJugador(Long id, AdminJugadorUpdateRequestDto request) {
        JugadorReal jugador = jugadorRepo.findById(id).orElseThrow();
        if (request.getEquipoRealId() != null) {
            EquipoReal equipo = equipoRepo.findById(request.getEquipoRealId()).orElseThrow();
            jugador.setEquipoReal(equipo);
        }
        if (request.getEstado() != null) {
            jugador.setEstado(request.getEstado());
        }
        if (request.getPosicion() != null) {
            jugador.setPosicion(request.getPosicion());
        }
        if (request.getValorMercadoActual() != null) {
            jugador.setValorMercadoActual(request.getValorMercadoActual());
        }
        jugadorRepo.save(jugador);
    }

    private AdminJugadorDto mapToDto(JugadorReal jugador) {
        return AdminJugadorDto.builder()
                .id(jugador.getId())
                .nombreCompleto(jugador.getNombreCompleto())
                .posicion(jugador.getPosicion())
                .estado(jugador.getEstado())
                .valorMercadoActual(jugador.getValorMercadoActual())
                .numeroCamiseta(jugador.getNumeroCamiseta())
                .equipoRealId(jugador.getEquipoReal() != null ? jugador.getEquipoReal().getId() : null)
                .equipoSigla(jugador.getEquipoReal() != null ? jugador.getEquipoReal().getSigla() : "N/A")
                .gesPerfilUrl(jugador.getGesPerfilUrl())
                .fotoUrl(jugador.getFotoUrl())
                .build();
    }
}

