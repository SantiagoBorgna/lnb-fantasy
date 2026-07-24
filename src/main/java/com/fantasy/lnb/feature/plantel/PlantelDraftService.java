package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Servicio exclusivo para gestionar los planteles de Torneos Privados (Modo Draft).
 * No hay restricciones de presupuesto, pero hay exclusividad de jugadores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlantelDraftService {

    private final PlantelJornadaRepository plantelRepo;
    private final JornadaRepository jornadaRepo;
    private final TorneoRepository torneoRepo;
    private final UsuarioRepository usuarioRepo;
    private final JugadorRealRepository jugadorRealRepo;
    private final JugadorPlantelRepository jugadorPlantelRepo;
    private final PlantelClonadoService clonadoService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.fantasy.lnb.feature.dt.DirectorTecnicoRepository dtRepo;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER TABLE plantel_jornada DROP INDEX uk_equipo_jornada;");
            log.info("Índice uk_equipo_jornada eliminado correctamente.");
        } catch (Exception e) {
            log.info("El índice uk_equipo_jornada no existe o ya fue eliminado.");
        }
    }

    // TODO: Inyectar un Mapper para PlantelDto. Por ahora reutilizaremos la lógica si es posible.

    /**
     * Obtiene el plantel activo de un usuario para una liga draft específica.
     */
    @Transactional
    public Optional<PlantelJornada> obtenerPlantelActivoEntity(Long usuarioId, Long torneoId) {

        // 1. Buscamos primero en la jornada EN_JUEGO
        Jornada jornadaEnJuego = jornadaRepo.findByEstado(EstadoJornada.EN_JUEGO).orElse(null);
        if (jornadaEnJuego != null) {
            Optional<PlantelJornada> plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                    usuarioId, jornadaEnJuego.getId(), torneoId);
            if (plantel.isPresent()) return plantel;
        }

        // 2. Si no, en ABIERTA_A_CAMBIOS
        Jornada jornadaAbierta = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS).orElse(null);
        if (jornadaAbierta != null) {
            Optional<PlantelJornada> plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                    usuarioId, jornadaAbierta.getId(), torneoId);
            if (plantel.isPresent()) return plantel;
            
            // Si no tiene para la abierta, intentamos clonarle el de la última FINALIZADA
            Jornada jornadaAnterior = jornadaRepo.findFirstByEstadoOrderByNumeroDesc(EstadoJornada.FINALIZADA).orElse(null);
            if (jornadaAnterior != null) {
                boolean tieneAnterior = plantelRepo.existsByUsuario_IdAndJornada_IdAndTorneo_Id(
                        usuarioId, jornadaAnterior.getId(), torneoId);
                if (tieneAnterior) {
                    Usuario usuario = usuarioRepo.findById(usuarioId).orElseThrow();
                    // PlantelClonadoService ya fue actualizado para clonar equipos draft!
                    // Pero necesitamos un método específico para forzar el clonado de UNO solo.
                    // Por ahora, asumimos que el clonado masivo de fin de fecha ya se encargó de esto.
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Valida si un jugador ya fue elegido por otro usuario en este torneo en la jornada actual.
     */
    @Transactional(readOnly = true)
    public boolean jugadorEstaLibreEnTorneo(Long jugadorRealId, Long torneoId) {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElse(null);
        if (jornadaActiva == null) return true; // Si no hay jornada, no hay planteles
        
        return !jugadorPlantelRepo.existsByPlantelJornada_Torneo_IdAndPlantelJornada_Jornada_IdAndJugadorReal_Id(
                torneoId, jornadaActiva.getId(), jugadorRealId);
    }

    /**
     * Agrega un jugador al plantel de draft, ignorando el presupuesto, pero validando la exclusividad.
     * Utilizado durante el Draft en vivo o asíncrono.
     */
    @Transactional(noRollbackFor = IllegalStateException.class)
    public void agregarJugadorPorDraft(Long usuarioId, Long torneoId, Long jugadorRealId, com.fantasy.lnb.feature.plantel.RolPlantel rol) {
        
        PlantelJornada plantel = obtenerPlantelActivoEntity(usuarioId, torneoId)
                .orElseGet(() -> crearPlantelInicialParaDraft(usuarioId, torneoId));

        if (!jugadorEstaLibreEnTorneo(jugadorRealId, torneoId)) {
            throw new IllegalStateException("El jugador ya fue elegido por otro equipo en esta liga Draft.");
        }

        // Límite de 10 jugadores
        if (plantel.getJugadores().size() >= 10) {
            throw new IllegalStateException("El plantel de Draft ya está completo (10 jugadores).");
        }

        JugadorReal jugadorReal = jugadorRealRepo.findById(jugadorRealId)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));

        validarLimitesPosicion(plantel, jugadorReal.getPosicion());
        validarLmitePorEquipo(plantel, jugadorReal.getEquipoReal().getId());

        JugadorPlantel nuevoFichaje = JugadorPlantel.builder()
                .plantelJornada(plantel)
                .jugadorReal(jugadorReal)
                .rol(rol)
                .precioDeCompra(0.0) // En Draft el precio no importa
                .build();

        plantel.getJugadores().add(nuevoFichaje);
        plantelRepo.save(plantel);
        
        log.info("[DRAFT] Usuario {} eligió a {} para el torneo {}", usuarioId, jugadorReal.getNombreCompleto(), torneoId);
    }

    public boolean dtEstaLibreEnTorneo(Long dtId, Long torneoId) {
        Jornada jornadaActiva = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElse(null);
        if (jornadaActiva == null) return true;
        
        return !plantelRepo.existsByTorneo_IdAndJornada_IdAndDt_Id(
                torneoId, jornadaActiva.getId(), dtId);
    }

    @Transactional
    public void agregarDtPorDraft(Long usuarioId, Long torneoId, Long dtId) {
        PlantelJornada plantel = obtenerPlantelActivoEntity(usuarioId, torneoId)
                .orElseGet(() -> crearPlantelInicialParaDraft(usuarioId, torneoId));

        if (!dtEstaLibreEnTorneo(dtId, torneoId)) {
            throw new IllegalStateException("El DT ya fue elegido por otro equipo en esta liga Draft.");
        }

        com.fantasy.lnb.feature.dt.DirectorTecnico dt = dtRepo.findById(dtId)
                .orElseThrow(() -> new IllegalArgumentException("DT no encontrado"));

        plantel.setDt(dt);
        plantelRepo.save(plantel);
        
        log.info("[DRAFT] Usuario {} eligió DT {} para el torneo {}", usuarioId, dt.getNombreCompleto(), torneoId);
    }

    private PlantelJornada crearPlantelInicialParaDraft(Long usuarioId, Long torneoId) {
        Jornada jornadaDestino = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElseThrow(() -> new IllegalStateException("No hay jornadas abiertas para inicializar el equipo de Draft."));
        
        Usuario usuario = usuarioRepo.findById(usuarioId).orElseThrow();
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();

        PlantelJornada nuevoPlantel = PlantelJornada.builder()
                .usuario(usuario)
                .torneo(torneo)
                .jornada(jornadaDestino)
                .formacion("1-2-2")
                .transferenciasUsadas(0)
                .puntajeObtenidoFecha(0.0)
                .build();
        
        return plantelRepo.save(nuevoPlantel);
    }

    public void validarLimitesPosicion(PlantelJornada plantel, com.fantasy.lnb.feature.mercado.PosicionJugador nuevaPos) {
        int grupo1 = 0; // BASE, ESCOLTA
        int grupo2 = 0; // ALERO, ALA_PIVOT
        int grupo3 = 0; // PIVOT

        for (JugadorPlantel jp : plantel.getJugadores()) {
            switch (jp.getJugadorReal().getPosicion()) {
                case BASE: case ESCOLTA: grupo1++; break;
                case ALERO: case ALA_PIVOT: grupo2++; break;
                case PIVOT: grupo3++; break;
            }
        }

        switch (nuevaPos) {
            case BASE: case ESCOLTA: 
                if (grupo1 >= 4) throw new IllegalStateException("Ya tenés el máximo de 4 Bases/Escoltas.");
                break;
            case ALERO: case ALA_PIVOT:
                if (grupo2 >= 4) throw new IllegalStateException("Ya tenés el máximo de 4 Aleros/Ala-Pivots.");
                break;
            case PIVOT:
                if (grupo3 >= 4) throw new IllegalStateException("Ya tenés el máximo de 4 Pivots.");
                break;
        }
    }

    public void validarLmitePorEquipo(PlantelJornada plantel, Long equipoLnbId) {
        long count = plantel.getJugadores().stream()
            .filter(jp -> jp.getJugadorReal().getEquipoReal().getId().equals(equipoLnbId))
            .count();
        if (count >= 2) {
            throw new IllegalStateException("Ya tenés 2 jugadores de ese mismo equipo. No podés elegir más.");
        }
    }

    @Transactional
    public void acomodarPlantelesPostDraft(Long torneoId) {
        Jornada jornadaDestino = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElse(null);
        if (jornadaDestino == null) return;
        
        java.util.List<PlantelJornada> planteles = plantelRepo.findByTorneo_IdAndJornada_Id(torneoId, jornadaDestino.getId());
        String[] formaciones = {"1-2-2", "1-3-1", "2-1-2", "2-2-1", "3-1-1"};
        
        for (PlantelJornada p : planteles) {
            if (p.getJugadores().size() != 10) continue; 
            
            java.util.List<JugadorPlantel> jugadores = new java.util.ArrayList<>(p.getJugadores());
            
            boolean acomodado = false;
            for (String f : formaciones) {
                if (intentarAcomodar(jugadores, f)) {
                    p.setFormacion(f);
                    acomodado = true;
                    break;
                }
            }
            if (!acomodado) {
                log.warn("No se pudo encontrar formación válida para el plantel {} en torneo {}", p.getId(), torneoId);
            }
            plantelRepo.save(p);
        }
    }

    private boolean intentarAcomodar(java.util.List<JugadorPlantel> jugadores, String formacion) {
        String[] partes = formacion.split("-");
        int reqGuards = Integer.parseInt(partes[0]);
        int reqForwards = Integer.parseInt(partes[1]);
        int reqCenters = Integer.parseInt(partes[2]);
        
        java.util.List<JugadorPlantel> guards = new java.util.ArrayList<>();
        java.util.List<JugadorPlantel> forwards = new java.util.ArrayList<>();
        java.util.List<JugadorPlantel> centers = new java.util.ArrayList<>();
        
        for (JugadorPlantel jp : jugadores) {
            com.fantasy.lnb.feature.mercado.PosicionJugador pos = jp.getJugadorReal().getPosicion();
            if (pos == com.fantasy.lnb.feature.mercado.PosicionJugador.BASE || pos == com.fantasy.lnb.feature.mercado.PosicionJugador.ESCOLTA) {
                guards.add(jp);
            } else if (pos == com.fantasy.lnb.feature.mercado.PosicionJugador.ALERO || pos == com.fantasy.lnb.feature.mercado.PosicionJugador.ALA_PIVOT) {
                forwards.add(jp);
            } else if (pos == com.fantasy.lnb.feature.mercado.PosicionJugador.PIVOT) {
                centers.add(jp);
            }
        }
        
        if (guards.size() <= reqGuards || forwards.size() <= reqForwards || centers.size() <= reqCenters) {
            return false;
        }
        
        // Reset all to SUPLENTE initially
        for (JugadorPlantel jp : jugadores) {
            jp.setRol(RolPlantel.SUPLENTE);
        }
        
        // Pick titulares
        for (int i=0; i<reqGuards; i++) guards.get(i).setRol(RolPlantel.TITULAR);
        for (int i=0; i<reqForwards; i++) forwards.get(i).setRol(RolPlantel.TITULAR);
        for (int i=0; i<reqCenters; i++) centers.get(i).setRol(RolPlantel.TITULAR);
        
        // Pick Sexto Hombre
        for (JugadorPlantel jp : jugadores) {
            if (jp.getRol() == RolPlantel.SUPLENTE) {
                jp.setRol(RolPlantel.SEXTO_HOMBRE);
                break;
            }
        }
        
        return true;
    }
}
