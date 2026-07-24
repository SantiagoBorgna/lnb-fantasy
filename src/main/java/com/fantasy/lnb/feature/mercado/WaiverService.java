package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.plantel.JugadorPlantel;
import com.fantasy.lnb.feature.plantel.PlantelDraftService;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TorneoEquipo;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fantasy.lnb.feature.mercado.dto.WaiverClaimDto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaiverService {

    private final WaiverClaimRepository claimRepo;
    private final TorneoEquipoRepository torneoEquipoRepo;
    private final JornadaRepository jornadaRepo;
    private final PlantelJornadaRepository plantelRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY COLUMN jugador_entra_id BIGINT NULL;");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY COLUMN dt_entra_id BIGINT NULL;");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY COLUMN dt_sale_id BIGINT NULL;");
            log.info("Table transaccion_draft altered successfully to allow nulls.");
        } catch (Exception e) {
            log.warn("Could not alter table transaccion_draft, it might already be correct.", e);
        }
    }

    private final PlantelDraftService plantelDraftService;
    private final JugadorRealRepository jugadorRepo;
    private final com.fantasy.lnb.feature.dt.DirectorTecnicoRepository dtRepo;
    private final UsuarioRepository usuarioRepo;
    private final TransaccionDraftRepository transaccionDraftRepo;
    private final PushNotificationService pushNotificationService;

    // Límite de reclamos de Waiver por fecha
    private static final int MAX_WAIVER_CLAIMS = 3;
    // Límite total de transferencias (Waivers + Agentes Libres)
    private static final int MAX_TRANSFERENCIAS = 4;

    @Transactional
    public void registrarReclamo(Long usuarioId, com.fantasy.lnb.feature.mercado.dto.WaiverClaimRequest request) {
        Long torneoId = request.getTorneoId();
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElseThrow(() -> new IllegalStateException("No hay jornadas abiertas para hacer reclamos."));

        PlantelJornada plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornadaActiva.getId(), torneoId)
                .orElseThrow(() -> new IllegalStateException("No tienes plantel activo en este torneo."));

        // 1. Validar límite de reclamos activos
        long reclamosPendientes = claimRepo.countByUsuario_IdAndTorneo_IdAndJornada_IdAndEstado(
                usuarioId, torneoId, jornadaActiva.getId(), EstadoClaim.PENDIENTE);
        
        if (reclamosPendientes >= MAX_WAIVER_CLAIMS) {
            throw new IllegalStateException("Ya alcanzaste el máximo de " + MAX_WAIVER_CLAIMS + " reclamos.");
        }

        // 2. Validar límite total de transferencias
        if (plantel.getTransferenciasUsadas() + reclamosPendientes >= MAX_TRANSFERENCIAS) {
            throw new IllegalStateException("Superas el límite de " + MAX_TRANSFERENCIAS + " transferencias por jornada.");
        }

        Usuario usuario = usuarioRepo.findById(usuarioId).orElseThrow();
        Torneo torneo = plantel.getTorneo();
        
        WaiverClaim.WaiverClaimBuilder builder = WaiverClaim.builder()
                .torneo(torneo)
                .usuario(usuario)
                .jornada(jornadaActiva);

        if (request.getJugadorEntranteId() != null) {
            // Es un reclamo de jugador
            if (!plantelDraftService.jugadorEstaLibreEnTorneo(request.getJugadorEntranteId(), torneoId)) {
                throw new IllegalStateException("El jugador elegido ya pertenece a otro equipo.");
            }
            JugadorReal elegido = jugadorRepo.findById(request.getJugadorEntranteId()).orElseThrow();
            JugadorReal cortado = null;
            if (request.getJugadorSalienteId() != null) {
                cortado = jugadorRepo.findById(request.getJugadorSalienteId()).orElseThrow();
                boolean loTiene = plantel.getJugadores().stream().anyMatch(j -> j.getJugadorReal().getId().equals(request.getJugadorSalienteId()));
                if (!loTiene) throw new IllegalStateException("El jugador a cortar no está en tu plantel.");
            } else if (plantel.getJugadores().size() >= 10) {
                throw new IllegalStateException("Debes elegir a un jugador para cortar, tu plantel está lleno.");
            }
            builder.jugadorElegido(elegido).jugadorCortado(cortado);
        } else if (request.getDtEntranteId() != null) {
            // Es un reclamo de DT
            // if (!plantelDraftService.dtEstaLibreEnTorneo(request.getDtEntranteId(), torneoId)) {
            //     throw new IllegalStateException("El DT elegido ya pertenece a otro equipo.");
            // }
            com.fantasy.lnb.feature.dt.DirectorTecnico dtElegido = dtRepo.findById(request.getDtEntranteId()).orElseThrow();
            com.fantasy.lnb.feature.dt.DirectorTecnico dtCortado = null;
            if (request.getDtSalienteId() != null) {
                dtCortado = dtRepo.findById(request.getDtSalienteId()).orElseThrow();
                if (plantel.getDt() == null || !plantel.getDt().getId().equals(request.getDtSalienteId())) {
                    throw new IllegalStateException("El DT a cortar no es tu DT actual.");
                }
            } else if (plantel.getDt() != null) {
                throw new IllegalStateException("Debes cortar a tu DT actual para elegir uno nuevo.");
            }
            builder.dtElegido(dtElegido).dtCortado(dtCortado);
        } else {
            throw new IllegalStateException("Debe especificar un jugador o DT entrante.");
        }

        WaiverClaim claim = builder.build();
        claimRepo.save(claim);
        log.info("[WAIVER] Usuario {} registró reclamo en torneo {}", usuarioId, torneoId);
    }

    /**
     * Procesa todos los reclamos pendientes a las 12 PM todos los días.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void procesarWaiversDiarios() {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS).orElse(null);
        if (jornadaActiva == null || jornadaActiva.getFechaInicio() == null) return;

        LocalDateTime limite = jornadaActiva.getFechaInicio().minusHours(4);
        if (LocalDateTime.now().isBefore(limite)) return; // Todavía es mercado restringido, no procesar aún

        procesarTodosLosWaiversPendientes(jornadaActiva);
    }

    @Transactional
    public void procesarWaiversForzado() {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS).orElse(null);
        if (jornadaActiva == null) return;
        procesarTodosLosWaiversPendientes(jornadaActiva);
    }

    private void procesarTodosLosWaiversPendientes(Jornada jornadaActiva) {
        List<WaiverClaim> pendientes = claimRepo.findByJornada_IdAndEstado(jornadaActiva.getId(), EstadoClaim.PENDIENTE);
        if (pendientes.isEmpty()) return;

        // Agrupar reclamos por Torneo
        Map<Torneo, List<WaiverClaim>> reclamosPorTorneo = pendientes.stream().collect(Collectors.groupingBy(WaiverClaim::getTorneo));

        for (Map.Entry<Torneo, List<WaiverClaim>> entry : reclamosPorTorneo.entrySet()) {
            Torneo torneo = entry.getKey();
            List<WaiverClaim> claimsTorneo = entry.getValue();

            procesarTorneo(torneo, jornadaActiva, claimsTorneo);
        }
    }

    private void procesarTorneo(Torneo torneo, Jornada jornada, List<WaiverClaim> claims) {
        log.info("[WAIVER] Procesando {} reclamos para el torneo {}", claims.size(), torneo.getId());

        List<TorneoEquipo> equipos = torneoEquipoRepo.findByTorneo_Id(torneo.getId());
        
        // Iteramos mientras haya reclamos pendientes y procesables
        boolean huboCambios = true;
        while (huboCambios) {
            huboCambios = false;
            
            // Ordenar equipos por prioridad (1 es la mejor)
            equipos.sort(Comparator.comparingInt(TorneoEquipo::getPrioridadWaiver));

            for (TorneoEquipo te : equipos) {
                // Buscar el primer reclamo pendiente de este usuario (ordenado por fecha de creación)
                WaiverClaim claim = claims.stream()
                        .filter(c -> c.getUsuario().getId().equals(te.getEquipoVirtual().getUsuario().getId()) && c.getEstado() == EstadoClaim.PENDIENTE)
                        .min(Comparator.comparing(WaiverClaim::getCreadoEn))
                        .orElse(null);

                if (claim != null) {
                    procesarReclamo(claim, te, equipos, claims);
                    huboCambios = true;
                    // Romper el for para reordenar la lista de prioridades desde el principio
                    break;
                }
            }
        }
            
        // Enviar notificaciones a los usuarios del torneo
        for (TorneoEquipo te : equipos) {
            Usuario usuario = te.getEquipoVirtual().getUsuario();
            long totalReclamos = claims.stream()
                    .filter(c -> c.getUsuario().getId().equals(usuario.getId()))
                    .count();
            long aprobados = claims.stream()
                    .filter(c -> c.getUsuario().getId().equals(usuario.getId()) && c.getEstado() == EstadoClaim.APROBADO)
                    .count();
            
            String mensaje;
            if (totalReclamos > 0) {
                mensaje = "La Agencia Restringida ha finalizado. Se ejecutaron " + aprobados + " de tus " + totalReclamos + " reclamos.";
            } else {
                mensaje = "La Agencia Restringida ha finalizado. Ya podés fichar jugadores libres.";
            }
            
            pushNotificationService.enviarNotificacionAUsuario(usuario, "Agencia Restringida Cerrada", mensaje);
        }
    }

    private void procesarReclamo(WaiverClaim claim, TorneoEquipo te, List<TorneoEquipo> equipos, List<WaiverClaim> claimsTorneo) {
        boolean esReclamoJugador = claim.getJugadorElegido() != null;
        
        // Verificar si el jugador o DT sigue libre
        boolean sigueLibre = esReclamoJugador ? 
            plantelDraftService.jugadorEstaLibreEnTorneo(claim.getJugadorElegido().getId(), claim.getTorneo().getId()) :
            !plantelRepo.existsByTorneo_IdAndJornada_IdAndDt_Id(claim.getTorneo().getId(), claim.getJornada().getId(), claim.getDtElegido().getId());

        if (!sigueLibre) {
            claim.setEstado(EstadoClaim.RECHAZADO);
            claim.setMotivoRechazo("Fichado por equipo con mayor prioridad");
            claimRepo.save(claim);
            return;
        }

        PlantelJornada plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                claim.getUsuario().getId(), claim.getJornada().getId(), claim.getTorneo().getId()).orElse(null);

        if (plantel == null || plantel.getTransferenciasUsadas() >= MAX_TRANSFERENCIAS) {
            claim.setEstado(EstadoClaim.RECHAZADO);
            claim.setMotivoRechazo("Límite de transferencias excedido o sin plantel");
            claimRepo.save(claim);
            return;
        }

        // Efectuar el cambio
          if (esReclamoJugador) {
              com.fantasy.lnb.feature.plantel.RolPlantel rolAnterior = com.fantasy.lnb.feature.plantel.RolPlantel.SUPLENTE;
              if (claim.getJugadorCortado() != null) {
                  rolAnterior = plantel.getJugadores().stream()
                      .filter(j -> j.getJugadorReal().getId().equals(claim.getJugadorCortado().getId()))
                      .findFirst()
                      .map(com.fantasy.lnb.feature.plantel.JugadorPlantel::getRol)
                      .orElse(com.fantasy.lnb.feature.plantel.RolPlantel.SUPLENTE);
                  plantel.getJugadores().removeIf(j -> j.getJugadorReal().getId().equals(claim.getJugadorCortado().getId()));
              }
  
              com.fantasy.lnb.feature.plantel.JugadorPlantel nuevo = com.fantasy.lnb.feature.plantel.JugadorPlantel.builder()
                      .plantelJornada(plantel)
                      .jugadorReal(claim.getJugadorElegido())
                      .rol(rolAnterior)
                      .precioDeCompra(0.0)
                      .build();
              plantel.getJugadores().add(nuevo);
          } else {
            plantel.setDt(claim.getDtElegido());
        }
        
        plantel.setTransferenciasUsadas(plantel.getTransferenciasUsadas() + 1);
        plantelRepo.save(plantel);

        claim.setEstado(EstadoClaim.APROBADO);
        claimRepo.save(claim);

        TransaccionDraft transaccion = TransaccionDraft.builder()
                .torneo(claim.getTorneo())
                .usuario(claim.getUsuario())
                .jornada(claim.getJornada())
                .jugadorEntra(claim.getJugadorElegido())
                .jugadorSale(claim.getJugadorCortado())
                .dtEntra(claim.getDtElegido())
                .dtSale(claim.getDtCortado())
                .tipo(TipoTransaccionDraft.WAIVER)
                .build();
        transaccionDraftRepo.save(transaccion);

        log.info("[WAIVER] Reclamo APROBADO: {} ficha a {}", claim.getUsuario().getId(), 
            esReclamoJugador ? claim.getJugadorElegido().getNombreCompleto() : claim.getDtElegido().getNombreCompleto());

        // Rechazar otros reclamos por el mismo jugador/DT
        for (WaiverClaim otro : claimsTorneo) {
            if (otro.getId().equals(claim.getId())) continue;
            if (otro.getEstado() == EstadoClaim.PENDIENTE) {
                boolean conflicto = esReclamoJugador ? 
                    (otro.getJugadorElegido() != null && otro.getJugadorElegido().getId().equals(claim.getJugadorElegido().getId())) :
                    (otro.getDtElegido() != null && otro.getDtElegido().getId().equals(claim.getDtElegido().getId()));

                if (conflicto) {
                    otro.setEstado(EstadoClaim.RECHAZADO);
                    otro.setMotivoRechazo("Fichado por equipo con mayor prioridad");
                    claimRepo.save(otro);
                }
            }
        }

        // Bajar la prioridad del equipo al último lugar
        int maxPrioridad = equipos.stream().mapToInt(TorneoEquipo::getPrioridadWaiver).max().orElse(equipos.size());
        te.setPrioridadWaiver(maxPrioridad + 1); // Se asigna uno más alto que el máximo
        torneoEquipoRepo.save(te);
        
        // Compactar las prioridades (ej: 2, 3, 4, 5 -> 1, 2, 3, 4)
        equipos.sort(Comparator.comparingInt(TorneoEquipo::getPrioridadWaiver));
        int prio = 1;
        for (TorneoEquipo eq : equipos) {
            eq.setPrioridadWaiver(prio++);
            torneoEquipoRepo.save(eq);
        }
    }

    // ── Consultas para la UI ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto> obtenerOrdenPrioridad(Long torneoId) {
            return torneoEquipoRepo.findByTorneo_Id(torneoId).stream()
                            .sorted(Comparator.comparing(TorneoEquipo::getPrioridadWaiver))
                            .map(te -> com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto.builder()
                                            .nombreEquipo(te.getEquipoVirtual().getNombre())
                                            .nombreUsuario(te.getEquipoVirtual().getUsuario().getNombreDisplay())
                                            .posicion(te.getPrioridadWaiver()) // Reusing 'posicion' for 'prioridad'
                                            .equipoVirtualId(te.getEquipoVirtual().getId())
                                            .build())
                            .toList();
    }

    @Transactional(readOnly = true)
    public List<WaiverClaimDto> obtenerMisReclamosPendientes(Long usuarioId, Long torneoId) {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElse(null);
        if (jornadaActiva == null) return List.of();

        return claimRepo.findByUsuario_IdAndTorneo_IdAndJornada_IdOrderByCreadoEnAsc(usuarioId, torneoId, jornadaActiva.getId())
                .stream()
                .map(c -> WaiverClaimDto.builder()
                        .id(c.getId())
                        .jugadorEntranteId(c.getJugadorElegido() != null ? c.getJugadorElegido().getId() : null)
                        .jugadorEntranteNombre(c.getJugadorElegido() != null ? c.getJugadorElegido().getNombreCompleto() : null)
                        .jugadorSalienteId(c.getJugadorCortado() != null ? c.getJugadorCortado().getId() : null)
                        .jugadorSalienteNombre(c.getJugadorCortado() != null ? c.getJugadorCortado().getNombreCompleto() : null)
                        .dtEntranteId(c.getDtElegido() != null ? c.getDtElegido().getId() : null)
                        .dtEntranteNombre(c.getDtElegido() != null ? c.getDtElegido().getNombreCompleto() : null)
                        .dtSalienteId(c.getDtCortado() != null ? c.getDtCortado().getId() : null)
                        .dtSalienteNombre(c.getDtCortado() != null ? c.getDtCortado().getNombreCompleto() : null)
                        .estado(c.getEstado().name())
                        .motivoRechazo(c.getMotivoRechazo())
                        .fechaSolicitud(c.getCreadoEn().toString())
                        .build())
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<com.fantasy.lnb.feature.mercado.dto.TransaccionDraftDto> obtenerTransaccionesTorneo(Long torneoId, org.springframework.data.domain.Pageable pageable) {
        java.util.List<TorneoEquipo> equiposTorneo = torneoEquipoRepo.findByTorneo_Id(torneoId);
        
        return transaccionDraftRepo.findByTorneo_IdOrderByFechaDesc(torneoId, pageable)
                .map(t -> {
                    String teamName = equiposTorneo.stream()
                            .filter(e -> e.getEquipoVirtual().getUsuario().getId().equals(t.getUsuario().getId()))
                            .map(e -> e.getEquipoVirtual().getNombre())
                            .findFirst()
                            .orElse("Equipo Desconocido");
                            
                    return com.fantasy.lnb.feature.mercado.dto.TransaccionDraftDto.builder()
                        .id(t.getId())
                        .equipoUsuarioId(t.getUsuario().getId())
                        .equipoUsuarioNombre(teamName)
                        .jugadorEntranteId(t.getJugadorEntra() != null ? t.getJugadorEntra().getId() : null)
                        .jugadorEntranteNombre(t.getJugadorEntra() != null ? t.getJugadorEntra().getNombreCompleto() : null)
                        .jugadorSalienteId(t.getJugadorSale() != null ? t.getJugadorSale().getId() : null)
                        .jugadorSalienteNombre(t.getJugadorSale() != null ? t.getJugadorSale().getNombreCompleto() : null)
                        .dtEntranteId(t.getDtEntra() != null ? t.getDtEntra().getId() : null)
                        .dtEntranteNombre(t.getDtEntra() != null ? t.getDtEntra().getNombreCompleto() : null)
                        .dtSalienteId(t.getDtSale() != null ? t.getDtSale().getId() : null)
                        .dtSalienteNombre(t.getDtSale() != null ? t.getDtSale().getNombreCompleto() : null)
                        .tipo(t.getTipo().name())
                        .fecha(t.getFecha())
                        .build();
                });
    }

    public boolean esFaseRestringida() {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS).orElse(null);
        return esFaseRestringida(jornadaActiva);
    }

    public static boolean esFaseRestringida(Jornada jornada) {
        if (jornada == null || jornada.getFechaInicio() == null) return false;

        LocalDateTime limite = jornada.getFechaInicio().minusHours(4);
        return LocalDateTime.now().isBefore(limite);
    }
}
