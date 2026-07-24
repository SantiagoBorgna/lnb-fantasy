package com.fantasy.lnb.feature.draft;

import com.fantasy.lnb.feature.plantel.PlantelDraftService;
import com.fantasy.lnb.feature.plantel.RolPlantel;
import com.fantasy.lnb.feature.torneo.DraftTurno;
import com.fantasy.lnb.feature.torneo.DraftTurnoRepository;
import com.fantasy.lnb.feature.torneo.EstadoDraft;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TorneoEquipo;
import com.fantasy.lnb.feature.torneo.TorneoH2HService;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftService {

    private final TorneoRepository torneoRepo;
    private final DraftTurnoRepository turnoRepo;
    private final PlantelDraftService plantelDraftService;
    private final JugadorRealRepository jugadorRepo;
    private final com.fantasy.lnb.feature.dt.DirectorTecnicoRepository dtRepo;
    private final TorneoH2HService h2hService;

    // Rondas del draft
    private static final int RONDAS_DRAFT = 11;
    // Tiempo por turno
    private static final int HORAS_POR_TURNO = 12;

    @Transactional
    public void iniciarDraft(Long torneoId, Long adminId) {
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
        
        if (!torneo.getCreador().getId().equals(adminId)) {
            throw new IllegalStateException("Solo el administrador puede iniciar el draft.");
        }
        if (torneo.getEstadoDraft() != EstadoDraft.PENDIENTE) {
            throw new IllegalStateException("El draft ya fue iniciado o ha finalizado.");
        }

        List<TorneoEquipo> participantes = torneo.getParticipantes();
        if (participantes.size() < 2) {
            throw new IllegalStateException("Se necesitan al menos 2 participantes para iniciar.");
        }

        // Orden aleatorio inicial
        List<Usuario> orden = new ArrayList<>(participantes.stream().map(te -> te.getEquipoVirtual().getUsuario()).toList());
        Collections.shuffle(orden);

        int numeroGlobal = 1;
        List<DraftTurno> turnos = new ArrayList<>();

        // Generar formato Snake
        for (int ronda = 1; ronda <= RONDAS_DRAFT; ronda++) {
            boolean esImpar = ronda % 2 != 0;
            
            // En rondas pares se invierte el orden
            List<Usuario> ordenRonda = new ArrayList<>(orden);
            if (!esImpar) {
                Collections.reverse(ordenRonda);
            }

            for (Usuario u : ordenRonda) {
                turnos.add(DraftTurno.builder()
                        .torneo(torneo)
                        .usuario(u)
                        .ronda(ronda)
                        .numeroTurnoGlobal(numeroGlobal++)
                        .build());
            }
        }

        turnoRepo.saveAll(turnos);

        torneo.setEstadoDraft(EstadoDraft.EN_CURSO);
        torneoRepo.save(torneo);

        log.info("[DRAFT] Torneo {} inició el draft. Orden generado.", torneoId);

        avanzarTurno(torneoId);
    }

    @Transactional
    public void elegirJugador(Long usuarioId, Long torneoId, Long jugadorRealId) {
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
        if (torneo.getEstadoDraft() != EstadoDraft.EN_CURSO) {
            throw new IllegalStateException("El draft no está en curso.");
        }

        DraftTurno turnoActual = turnoRepo.findFirstByTorneo_IdAndCompletadoFalseOrderByNumeroTurnoGlobalAsc(torneoId)
                .orElseThrow(() -> new IllegalStateException("No hay turnos pendientes."));

        if (!turnoActual.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalStateException("No es tu turno de elegir.");
        }

        if (turnoActual.getRonda() == 11) {
            throw new IllegalStateException("En esta ronda debes elegir un Director Técnico.");
        }

        // Determinar rol inicial (se puede cambiar después)
        RolPlantel rol = (turnoActual.getRonda() <= 5) ? RolPlantel.TITULAR : 
                         (turnoActual.getRonda() == 6) ? RolPlantel.SEXTO_HOMBRE : RolPlantel.SUPLENTE;

        // Validaciones e inserción en el roster
        plantelDraftService.agregarJugadorPorDraft(usuarioId, torneoId, jugadorRealId, rol);

        turnoActual.setCompletado(true);
        turnoActual.setJugadorRealIdElegido(jugadorRealId);
        turnoActual.setFueAutoPick(false);
        turnoRepo.save(turnoActual);

        log.info("[DRAFT] Torneo {} - Turno {} completado por {}", torneoId, turnoActual.getNumeroTurnoGlobal(), usuarioId);

        avanzarTurno(torneoId);
    }

    @Transactional
    public void elegirDt(Long usuarioId, Long torneoId, Long dtId) {
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
        if (torneo.getEstadoDraft() != EstadoDraft.EN_CURSO) {
            throw new IllegalStateException("El draft no está en curso.");
        }

        DraftTurno turnoActual = turnoRepo.findFirstByTorneo_IdAndCompletadoFalseOrderByNumeroTurnoGlobalAsc(torneoId)
                .orElseThrow(() -> new IllegalStateException("No hay turnos pendientes."));

        if (!turnoActual.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalStateException("No es tu turno de elegir.");
        }

        if (turnoActual.getRonda() != 11) {
            throw new IllegalStateException("Aún no es momento de elegir Director Técnico.");
        }

        plantelDraftService.agregarDtPorDraft(usuarioId, torneoId, dtId);

        turnoActual.setCompletado(true);
        turnoActual.setDtIdElegido(dtId);
        turnoActual.setFueAutoPick(false);
        turnoRepo.save(turnoActual);

        log.info("[DRAFT] Torneo {} - Turno {} completado por {} (Elijio DT)", torneoId, turnoActual.getNumeroTurnoGlobal(), usuarioId);

        avanzarTurno(torneoId);
    }

    @Transactional
    public void avanzarTurno(Long torneoId) {
        turnoRepo.findFirstByTorneo_IdAndCompletadoFalseOrderByNumeroTurnoGlobalAsc(torneoId).ifPresentOrElse(
            siguiente -> {
                siguiente.setInicioTurno(LocalDateTime.now());
                siguiente.setLimiteTiempo(LocalDateTime.now().plusSeconds(5));
                turnoRepo.save(siguiente);
                log.info("[DRAFT] Torneo {} - Es el turno de {} (Expira: {})", torneoId, siguiente.getUsuario().getId(), siguiente.getLimiteTiempo());
                // TODO: Enviar notificación push/email al usuario
            },
            () -> {
                // No hay más turnos
                Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
                torneo.setEstadoDraft(EstadoDraft.FINALIZADO);
                
                // Acomodar los planteles a una formación válida
                plantelDraftService.acomodarPlantelesPostDraft(torneoId);

                // Inicializar lista de prioridad de Waivers (aleatoria)
                List<TorneoEquipo> equipos = torneo.getParticipantes();
                Collections.shuffle(equipos);
                int prioridad = 1;
                for (TorneoEquipo te : equipos) {
                    te.setPrioridadWaiver(prioridad++);
                }

                torneoRepo.save(torneo);
                h2hService.generarFixture(torneo); // Generar el Round Robin
                log.info("[DRAFT] Torneo {} - DRAFT FINALIZADO. Prioridades de Waiver asignadas y Fixture H2H generado.", torneoId);
            }
        );
    }

    // Tarea programada que corre cada 10s para procesar AutoPicks
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void procesarAutoPicksVencidos() {
        List<DraftTurno> vencidos = turnoRepo.findByCompletadoFalseAndLimiteTiempoBefore(LocalDateTime.now());
        for (DraftTurno turno : vencidos) {
            log.info("[DRAFT AUTO-PICK] Procesando turno {} vencido del torneo {}", turno.getNumeroTurnoGlobal(), turno.getTorneo().getId());
            
            if (turno.getRonda() == 11) {
                List<com.fantasy.lnb.feature.dt.DirectorTecnico> dts = dtRepo.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "nombreCompleto"));
                for (com.fantasy.lnb.feature.dt.DirectorTecnico dt : dts) {
                    if (plantelDraftService.dtEstaLibreEnTorneo(dt.getId(), turno.getTorneo().getId())) {
                        try {
                            plantelDraftService.agregarDtPorDraft(turno.getUsuario().getId(), turno.getTorneo().getId(), dt.getId());
                            turno.setCompletado(true);
                            turno.setDtIdElegido(dt.getId());
                            turno.setFueAutoPick(true);
                            turnoRepo.save(turno);
                            avanzarTurno(turno.getTorneo().getId());
                            break;
                        } catch (Exception e) {
                            log.error("Error auto-pick DT", e);
                        }
                    }
                }
            } else {
                // Lógica simple: buscar el mejor jugador disponible (ordenado por precio) que no esté en el torneo y no sea DESCONOCIDO
                List<JugadorReal> todos = jugadorRepo.findByPosicionNot(com.fantasy.lnb.feature.mercado.PosicionJugador.DESCONOCIDO, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "valorMercadoActual"));
                for (JugadorReal j : todos) {
                    if (plantelDraftService.jugadorEstaLibreEnTorneo(j.getId(), turno.getTorneo().getId())) {
                        // Elegirlo
                        try {
                            RolPlantel rol = (turno.getRonda() <= 5) ? RolPlantel.TITULAR : RolPlantel.SUPLENTE;
                            plantelDraftService.agregarJugadorPorDraft(turno.getUsuario().getId(), turno.getTorneo().getId(), j.getId(), rol);
                            turno.setCompletado(true);
                            turno.setJugadorRealIdElegido(j.getId());
                            turno.setFueAutoPick(true);
                            turnoRepo.save(turno);
                            avanzarTurno(turno.getTorneo().getId());
                            break;
                        } catch (IllegalStateException e) {
                            // Limite de posición u otro límite esperado, continuar intentando
                        } catch (Exception e) {
                            log.error("Error auto-pick", e);
                        }
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public com.fantasy.lnb.feature.draft.dto.DraftStateDto obtenerEstadoDraft(Long torneoId) {
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
        List<DraftTurno> turnos = turnoRepo.findByTorneo_IdOrderByNumeroTurnoGlobalAsc(torneoId);
        
        Long turnoActualId = null;
        if (torneo.getEstadoDraft() == EstadoDraft.EN_CURSO) {
            turnoActualId = turnos.stream()
                .filter(t -> !t.getCompletado())
                .findFirst()
                .map(DraftTurno::getId)
                .orElse(null);
        }

        List<com.fantasy.lnb.feature.draft.dto.DraftTurnoDto> turnosDto = turnos.stream().map(t -> {
            String nombreEquipo = t.getTorneo().getParticipantes().stream()
                .filter(te -> te.getEquipoVirtual().getUsuario().getId().equals(t.getUsuario().getId()))
                .findFirst()
                .map(te -> te.getEquipoVirtual().getNombre())
                .orElse("Equipo");

            return com.fantasy.lnb.feature.draft.dto.DraftTurnoDto.builder()
                .id(t.getId())
                .usuarioId(t.getUsuario().getId())
                .nombreUsuario(t.getUsuario().getNombreDisplay())
                .nombreEquipo(nombreEquipo)
                .ronda(t.getRonda())
                .numeroTurnoGlobal(t.getNumeroTurnoGlobal())
                .completado(t.getCompletado())
                .jugadorRealIdElegido(t.getJugadorRealIdElegido())
                .nombreJugadorElegido(t.getJugadorRealIdElegido() != null ? jugadorRepo.findById(t.getJugadorRealIdElegido()).map(JugadorReal::getNombreCompleto).orElse(null) : null)
                .dtIdElegido(t.getDtIdElegido())
                .nombreDtElegido(t.getDtIdElegido() != null ? dtRepo.findById(t.getDtIdElegido()).map(com.fantasy.lnb.feature.dt.DirectorTecnico::getNombreCompleto).orElse(null) : null)
                .inicioTurno(t.getInicioTurno())
                .limiteTiempo(t.getLimiteTiempo())
                .fueAutoPick(t.getFueAutoPick())
                .build();
        }).toList();

        return com.fantasy.lnb.feature.draft.dto.DraftStateDto.builder()
            .estado(torneo.getEstadoDraft())
            .turnos(turnosDto)
            .turnoActualId(turnoActualId)
            .cantidadParticipantes(torneo.getParticipantes().size())
            .maxParticipantes(torneo.getMaxParticipantes())
            .adminId(torneo.getCreador().getId())
            .build();
    }
}
