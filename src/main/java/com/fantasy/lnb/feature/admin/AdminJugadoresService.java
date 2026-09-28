package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJugadorDto;
import com.fantasy.lnb.feature.admin.dto.AdminJugadorUpdateRequestDto;
import com.fantasy.lnb.feature.admin.dto.EquipoRealBasicoDto;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.plantel.JugadorPlantelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminJugadoresService {

    private final JugadorRealRepository jugadorRepo;
    private final EquipoRealRepository equipoRepo;
    private final JugadorPlantelRepository jugadorPlantelRepo;

    @Transactional(readOnly = true)
    public List<AdminJugadorDto> getAllJugadores() {
        Map<Long, Integer> plantelesCount = jugadorPlantelRepo.countJugadoresEnPlantelesActuales().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        Map<Long, Integer> capitanesCount = jugadorPlantelRepo.countCapitanesEnPlantelesActuales().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        return jugadorRepo.findAll().stream().map(jugador -> {
            AdminJugadorDto dto = mapToDto(jugador);
            dto.setCantidadPlanteles(plantelesCount.getOrDefault(jugador.getId(), 0));
            dto.setCantidadCapitan(capitanesCount.getOrDefault(jugador.getId(), 0));
            return dto;
        }).collect(Collectors.toList());
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
        if (request.getNumeroCamiseta() != null) {
            jugador.setNumeroCamiseta(request.getNumeroCamiseta());
        }
        jugadorRepo.save(jugador);
    }

    /**
     * Fusiona dos JugadorReal que representan a la misma persona real (típicamente
     * un jugador cargado a mano cuyo nombre no matcheó con el que trajo el
     * crawler). Conserva idPrincipal — con todo su historial de planteles,
     * traspasos, waivers, etc. intacto — y le copia encima los datos verificados
     * de GES del duplicado, que después se borra.
     *
     * No repunta manualmente las FKs de otras tablas a propósito: en vez de
     * mantener una lista de tablas que puede quedar desactualizada, dejamos que
     * la base de datos rechace el borrado si el duplicado todavía tiene alguna
     * referencia que no contemplamos acá.
     */
    @Transactional
    public Map<String, Object> fusionarJugadores(Long idPrincipal, Long idDuplicado) {
        if (idPrincipal == null || idDuplicado == null) {
            throw new IllegalArgumentException("Hay que indicar idPrincipal e idDuplicado.");
        }
        if (idPrincipal.equals(idDuplicado)) {
            throw new IllegalArgumentException("idPrincipal e idDuplicado no pueden ser el mismo jugador.");
        }

        JugadorReal principal = jugadorRepo.findById(idPrincipal)
                .orElseThrow(() -> new IllegalArgumentException("No existe el jugador principal id=" + idPrincipal));
        JugadorReal duplicado = jugadorRepo.findById(idDuplicado)
                .orElseThrow(() -> new IllegalArgumentException("No existe el jugador duplicado id=" + idDuplicado));

        if (duplicado.getGesId() == null) {
            throw new IllegalArgumentException(
                    "El jugador duplicado (id=" + idDuplicado + ") no tiene gesId — " +
                            "¿los ids están al revés? idDuplicado debería ser el que el crawler creó con datos de GES.");
        }

        if (jugadorPlantelRepo.existsByJugadorReal_Id(idDuplicado)) {
            throw new IllegalStateException(
                    "El jugador duplicado (id=" + idDuplicado + ") ya está en algún plantel de usuario — " +
                            "fusionarlo automáticamente lo borraría de ese plantel. Resolver a mano.");
        }

        String nombreAnterior = principal.getNombreCompleto();

        // Capturamos los datos de GES ANTES de borrar — el objeto Java sigue
        // teniendo los valores cargados aunque la fila ya no exista en la tabla.
        Long gesId = duplicado.getGesId();
        String nombreGes = duplicado.getNombreCompleto();
        var equipoGes = duplicado.getEquipoReal();
        String gesPerfilUrl = duplicado.getGesPerfilUrl();
        String fotoUrl = duplicado.getFotoUrl();
        var fechaNacimiento = duplicado.getFechaNacimiento();

        // IMPORTANTE: hay que borrar el duplicado ANTES de pisarle el gesId a
        // principal. ges_id es UNIQUE y MySQL lo chequea al toque (no al commit
        // como Postgres) — si asignáramos el gesId a principal mientras la fila
        // de duplicado todavía existe con ese mismo valor, el UPDATE choca contra
        // la restricción de unicidad.
        try {
            jugadorRepo.delete(duplicado);
            jugadorRepo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "No se pudo borrar el jugador duplicado (id=" + idDuplicado + "): todavía tiene " +
                            "referencias en otra tabla (waivers, traspasos, showdown, estadísticas, etc.). " +
                            "Resolver esas referencias a mano antes de fusionar.");
        }

        principal.setGesId(gesId);
        principal.setNombreCompleto(nombreGes);
        principal.setEquipoReal(equipoGes);
        principal.setGesPerfilUrl(gesPerfilUrl);
        principal.setFotoUrl(fotoUrl);
        principal.setFechaNacimiento(fechaNacimiento);
        jugadorRepo.save(principal);

        log.info("[ADMIN] Fusión: id={} ('{}') absorbió los datos de GES de id={} y quedó como '{}'.",
                idPrincipal, nombreAnterior, idDuplicado, principal.getNombreCompleto());

        return Map.of(
                "mensaje", "Fusión completada",
                "idConservado", idPrincipal,
                "nombreAnterior", nombreAnterior,
                "nombreActual", principal.getNombreCompleto(),
                "idEliminado", idDuplicado);
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
                .promedioFantasy(jugador.getPromedioFantasy())
                .build();
    }
}

