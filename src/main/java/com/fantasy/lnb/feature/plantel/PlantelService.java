package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.exception.FormacionInvalidaException;
import com.fantasy.lnb.exception.JornadaEnJuegoException;
import com.fantasy.lnb.exception.PresupuestoInsuficienteException;
import com.fantasy.lnb.exception.TransferenciasAgotadasException;
import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.dt.DirectorTecnicoRepository;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.dto.GuardarPlantelRequest;
import com.fantasy.lnb.feature.plantel.dto.JugadorEstadisticaDto;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaRequest;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaResultadoDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.OnboardingService;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantelService {

        private final PlantelJornadaRepository plantelRepo;
        private final JugadorRealRepository jugadorRepo;
        private final JornadaRepository jornadaRepo;
        private final UsuarioRepository usuarioRepo;
        private final EquipoVirtualRepository equipoVirtualRepo;
        private final JugadorPlantelRepository jugadorPlantelRepo;
        private final DirectorTecnicoRepository dtRepo;
        private final OnboardingService onboardingService;
        private final com.fantasy.lnb.feature.mercado.TransaccionDraftRepository transaccionDraftRepo;
        private final EstadisticaPartidoRepository estadisticaRepo;
        private final PartidoRepository partidoRepo;
        private final MotorPuntuacionPlantel motorPuntuacionPlantel;
        private final com.fantasy.lnb.feature.mercado.TraspasoUsuarioService traspasoUsuarioService;

        // ── Consulta ────────────────────────────────────────────────────────────

        /**
         * Devuelve el plantel del usuario para la jornada activa.
         *
         * Lógica de resolución:
         * 1. Buscar jornada activa (EN_JUEGO o ABIERTA_A_CAMBIOS)
         * 2. Si existe plantel para esa jornada → devolverlo
         * 3. Si NO existe → buscar plantel de la jornada anterior
         * 3a. Si existe plantel anterior → clonarlo y devolverlo
         * 3b. Si no hay plantel anterior → devolver empty (primera vez)
         */
        @Transactional
        public Optional<PlantelDto> obtenerPlantelActivo(Long usuarioId) {

                // 1. Buscamos primero en la jornada EN_JUEGO
                Jornada jornadaEnJuego = jornadaRepo.findByEstado(EstadoJornada.EN_JUEGO).orElse(null);
                if (jornadaEnJuego != null) {
                        Optional<PlantelJornada> plantelEnJuego = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId,
                                        jornadaEnJuego.getId());
                        if (plantelEnJuego.isPresent()) {
                                return plantelEnJuego.map(p -> toDto(p, obtenerPresupuesto(usuarioId)));
                        }
                }

                // 2. Si no tiene en juego (ej: usuario nuevo), buscamos en la próxima
                // ABIERTA_A_CAMBIOS
                Jornada jornadaAbierta = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS).orElse(null);
                if (jornadaAbierta != null) {
                        Optional<PlantelJornada> plantelAbierto = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId,
                                        jornadaAbierta.getId());
                        if (plantelAbierto.isPresent()) {
                                return plantelAbierto.map(p -> toDto(p, obtenerPresupuesto(usuarioId)));
                        }

                        // 3. Si tampoco tiene para la abierta, intentamos clonarle el de la última
                        // FINALIZADA
                        Jornada jornadaAnterior = jornadaRepo
                                        .findFirstByEstadoOrderByNumeroDesc(EstadoJornada.FINALIZADA).orElse(null);
                        if (jornadaAnterior != null) {
                                boolean tieneAnterior = plantelRepo.existsByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId,
                                                jornadaAnterior.getId());
                                if (tieneAnterior) {
                                        Usuario usuario = usuarioRepo.findById(usuarioId)
                                                        .orElseThrow(() -> new IllegalStateException(
                                                                        "Usuario no encontrado: " + usuarioId));
                                        PlantelJornada clonado = clonarPlantelAnterior(usuario, jornadaAnterior,
                                                        jornadaAbierta);
                                        return Optional.of(toDto(clonado, obtenerPresupuesto(usuarioId)));
                                }
                        }
                }

                // Si llega acá, es un usuario nuevo que todavía no completó el onboarding
                return Optional.empty();
        }

        @Transactional(readOnly = true)
        public List<JugadorEstadisticaDto> obtenerEstadisticasJornada(
                        Long usuarioId, Long jornadaId, Long torneoId) {

                PlantelJornada plantel;
                if (torneoId != null) {
                    plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornadaId, torneoId)
                                .orElseThrow(() -> new IllegalArgumentException("No existe plantel de draft para esa jornada."));
                } else {
                    plantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException("No existe plantel clásico para esa jornada."));
                }

                return plantel.getJugadores().stream()
                                .map(jp -> {
                                        Long jugadorId = jp.getJugadorReal().getId();

                                        return estadisticaRepo
                                                        .findByJugadorReal_IdAndJornada_Id(jugadorId, jornadaId)
                                                        .map(e -> JugadorEstadisticaDto.builder()
                                                                        .jugadorRealId(jugadorId)
                                                                        .puntajeFantasy(e.getPuntajeFantasyCalculado())
                                                                        .puntos(e.getPuntos())
                                                                        .rebotesDefensivos(e.getRebotesDefensivos())
                                                                        .rebotesOfensivos(e.getRebotesOfensivos())
                                                                        .asistencias(e.getAsistencias())
                                                                        .recuperaciones(e.getRecuperaciones())
                                                                        .perdidas(e.getPerdidas())
                                                                        .faltasCometidas(e.getFaltasCometidas())
                                                                        .faltasRecibidas(e.getFaltasRecibidas())
                                                                        .fueTitular(e.getFueTitularEnPartidoReal())
                                                                        .gano(e.getEquipoGano())
                                                                        .taponesRealizados(e.getTaponesRealizados())
                                                                        .taponesRecibidos(e.getTaponesRecibidos())
                                                                        .tirosDeCampoFallados(e.getTirosCampoFallados())
                                                                        .tirosLibresFallados(e.getTirosLibresFallados())
                                                                        .jugó(true)
                                                                        .build())
                                                        .orElse(JugadorEstadisticaDto.builder()
                                                                        .jugadorRealId(jugadorId)
                                                                        .jugó(false)
                                                                        .build());
                                })
                                .toList();
        }

        /**
         * Devuelve el plantel histórico de un usuario para una jornada específica.
         */
        @Transactional(readOnly = true)
        @org.springframework.cache.annotation.Cacheable(value = "plantel_historico", key = "#usuarioId + '_' + #jornadaId", condition = "@jornadaCacheHelper.estaFinalizada(#jornadaId)")
        public Optional<PlantelDto> obtenerPlantelHistorico(Long usuarioId, Long jornadaId) {
                return plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornadaId)
                                .map(p -> toDto(p, 0.0));
        }

        /**
         * Devuelve el plantel histórico de la jornada anterior (si existe) en contexto Torneo.
         */
        @Transactional(readOnly = true)
        @org.springframework.cache.annotation.Cacheable(value = "plantel_historico_torneo", key = "#torneoId + '_' + #usuarioId + '_' + #jornadaId", condition = "@jornadaCacheHelper.estaFinalizada(#jornadaId)")
        public Optional<PlantelDto> obtenerPlantelHistoricoTorneo(Long usuarioId, Long jornadaId, Long torneoId) {
                return plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornadaId, torneoId)
                                .map(p -> toDto(p, 0.0));
        }

        /**
         * Devuelve el plantel activo del usuario en contexto Torneo (Draft).
         */
        @Transactional(readOnly = true)
        public Optional<PlantelDto> obtenerPlantelTorneo(Long usuarioId, Long torneoId) {
                return jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.EN_JUEGO)
                                .flatMap(jornada -> plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId,
                                                jornada.getId(), torneoId))
                                .map(plantel -> toDto(plantel, 0.0))
                                .or(() -> {
                                        return jornadaRepo
                                                        .findFirstByEstadoOrderByFechaInicioAsc(
                                                                        EstadoJornada.ABIERTA_A_CAMBIOS)
                                                        .flatMap(j -> plantelRepo
                                                                        .findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId,
                                                                                        j.getId(), torneoId))
                                                        .map(p -> toDto(p, 0.0));
                                });
        }

        /**
         * Devuelve el plantel de otro jugador para una jornada específica.
         * Requiere que la jornada esté EN_JUEGO o FINALIZADA por seguridad.
         */
        @Transactional(readOnly = true)
        public Optional<PlantelDto> obtenerPlantelAjeno(Long equipoVirtualId, Long jornadaId, Long torneoId) {
                Jornada jornada = jornadaRepo.findById(jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada"));
                
                if (jornada.getEstado() == EstadoJornada.ABIERTA_A_CAMBIOS) {
                        throw new IllegalStateException("No podés ver el equipo de otro jugador mientras la jornada está abierta a cambios.");
                }

                if (jornada.getEstado() == EstadoJornada.FINALIZADA) {
                        Jornada ultimaFinalizada = jornadaRepo.findFirstByEstadoOrderByNumeroDesc(EstadoJornada.FINALIZADA).orElse(null);
                        if (ultimaFinalizada != null && !jornada.getId().equals(ultimaFinalizada.getId())) {
                                throw new IllegalStateException("Solo podés ver el equipo de la última jornada finalizada o la jornada en juego.");
                        }
                }

                EquipoVirtual equipo = equipoVirtualRepo.findById(equipoVirtualId)
                                .orElseThrow(() -> new IllegalArgumentException("Equipo virtual no encontrado"));
                
                if (torneoId != null) {
                    return plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(equipo.getUsuario().getId(), jornadaId, torneoId)
                            .map(p -> toDto(p, 0.0));
                }

                return plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(equipo.getUsuario().getId(), jornadaId)
                                .map(p -> toDto(p, equipo.getPresupuestoActual()));
        }

        /**
         * Devuelve las estadsticas de otro jugador para una jornada especfica.
         * Requiere que la jornada est EN_JUEGO o FINALIZADA por seguridad.
         */
        @Transactional(readOnly = true)
        public List<JugadorEstadisticaDto> obtenerEstadisticasAjenas(Long equipoVirtualId, Long jornadaId, Long torneoId) {
                Jornada jornada = jornadaRepo.findById(jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada"));
                
                if (jornada.getEstado() == EstadoJornada.ABIERTA_A_CAMBIOS) {
                        throw new IllegalStateException("No podés ver las estadísticas de otro jugador mientras la jornada está abierta a cambios.");
                }

                if (jornada.getEstado() == EstadoJornada.FINALIZADA) {
                        Jornada ultimaFinalizada = jornadaRepo.findFirstByEstadoOrderByNumeroDesc(EstadoJornada.FINALIZADA).orElse(null);
                        if (ultimaFinalizada != null && !jornada.getId().equals(ultimaFinalizada.getId())) {
                                throw new IllegalStateException("Solo podés ver las estadísticas de la última jornada finalizada o la jornada en juego.");
                        }
                }

                EquipoVirtual equipo = equipoVirtualRepo.findById(equipoVirtualId)
                                .orElseThrow(() -> new IllegalArgumentException("Equipo virtual no encontrado"));

                return obtenerEstadisticasJornada(equipo.getUsuario().getId(), jornadaId, torneoId);
        }

        @Transactional(readOnly = true)
        public Optional<PlantelDto> obtenerPlantelAjenoActual(Long equipoVirtualId, Long torneoId) {
                EquipoVirtual equipo = equipoVirtualRepo.findById(equipoVirtualId)
                                .orElseThrow(() -> new IllegalArgumentException("Equipo virtual no encontrado"));
                
                if (torneoId != null) {
                        return obtenerPlantelTorneo(equipo.getUsuario().getId(), torneoId);
                } else {
                        return obtenerPlantelActivo(equipo.getUsuario().getId());
                }
        }

        @Transactional(readOnly = true)
        public List<JugadorEstadisticaDto> obtenerEstadisticasAjenasActual(Long equipoVirtualId, Long torneoId) {
                EquipoVirtual equipo = equipoVirtualRepo.findById(equipoVirtualId)
                                .orElseThrow(() -> new IllegalArgumentException("Equipo virtual no encontrado"));
                
                Jornada jornadaEnJuego = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.EN_JUEGO).orElse(null);
                
                if (jornadaEnJuego != null) {
                    try {
                        return obtenerEstadisticasJornada(equipo.getUsuario().getId(), jornadaEnJuego.getId(), torneoId);
                    } catch (IllegalArgumentException e) {
                        // Si falla porque no existe plantel en la jornada en juego, probamos con la abierta a cambios
                    }
                }
                
                Jornada jornadaAbierta = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(() -> new IllegalArgumentException("No hay jornada activa"));

                return obtenerEstadisticasJornada(equipo.getUsuario().getId(), jornadaAbierta.getId(), torneoId);
        }

        // ── Armado del plantel ──────────────────────────────────────────────────

        /**
         * Guarda o reemplaza el plantel del usuario para la jornada activa.
         *
         * Validaciones en orden:
         * 1. Existe jornada disponible (ABIERTA o EN_JUEGO)
         * 2. La jornada NO está EN_JUEGO (no se permiten cambios)
         * 3. Exactamente 10 jugadores + 1 DT
         * 4. Exactamente 1 CAPITAN y 1 SEXTO_HOMBRE
         * 5. Formación válida y compatible con posiciones de titulares
         * 6. Presupuesto suficiente
         */
        /**
         * POST /api/plantel
         * Arma el plantel inicial o actualiza la formación y roles del existente.
         */
        @Transactional
        public PlantelDto guardarPlantel(Long usuarioId, GuardarPlantelRequest request) {

                // ── 1. Resolver jornada destino ─────────────────────────
                // Buscamos la primera jornada disponible para recibir cambios
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByNumeroAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(() -> new IllegalStateException(
                                                "No hay ninguna jornada abierta para cambios ahora mismo."));

                // ── 2. Validaciones básicas ──────────────────────────────────────
                if (request.getJugadores() == null || request.getJugadores().size() != 10) {
                        throw new IllegalArgumentException("El plantel debe tener exactamente 10 jugadores.");
                }

                List<JugadorReal> jugadoresReales = cargarJugadores(request.getJugadores());
                validarRolesUnicos(request.getJugadores());
                validarLimiteJugadoresPorEquipo(jugadoresReales);
                validarComposicionBanco(request.getJugadores(), jugadoresReales);

                DirectorTecnico dt = dtRepo.findById(request.getDtId())
                                .orElseThrow(() -> new IllegalArgumentException("Director Técnico no encontrado."));

                List<PosicionJugador> posicionesTitulares = obtenerPosicionesTitulares(request.getJugadores(),
                                jugadoresReales);
                if (!FormacionValidator.esValida(request.getFormacion(), posicionesTitulares)) {
                        throw new FormacionInvalidaException(request.getFormacion());
                }

                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException("Equipo virtual no encontrado."));

                // ── 3. Verificar si ya existe un plantel ─────────────────────────
                Optional<PlantelJornada> optPlantel;
                if (request.getTorneoId() != null) {
                        optPlantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornada.getId(), request.getTorneoId());
                } else {
                        optPlantel = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornada.getId());
                }
                PlantelJornada plantelAGuardar;

                if (optPlantel.isPresent()) {
                        // =========================================================
                        // MODO ACTUALIZACIÓN (Autoguardado desde la canchita)
                        // =========================================================
                        plantelAGuardar = optPlantel.get();
                        plantelAGuardar.setFormacion(request.getFormacion());

                        // Solo actualizamos los roles de los jugadores existentes
                        for (GuardarPlantelRequest.SlotJugadorRequest slot : request.getJugadores()) {
                                plantelAGuardar.getJugadores().stream()
                                                .filter(jp -> jp.getJugadorReal().getId()
                                                                .equals(slot.getJugadorRealId()))
                                                .findFirst()
                                                .ifPresent(jp -> jp.setRol(slot.getRol()));
                        }

                        log.info("[PLANTEL] Usuario {} actualizó su táctica/roles.", usuarioId);

                } else {
                        // =========================================================
                        // MODO CREACIÓN (Onboarding)
                        // =========================================================
                        double costoTotal = calcularCostoTotal(jugadoresReales);
                        if (equipo.getPresupuestoActual() < costoTotal) {
                                throw new PresupuestoInsuficienteException(equipo.getPresupuestoActual(), costoTotal);
                        }

                        plantelAGuardar = PlantelJornada.builder()
                                        .usuario(usuarioRepo.findById(usuarioId).orElseThrow())
                                        .jornada(jornada)
                                        .dt(dt)
                                        .formacion(request.getFormacion())
                                        .transferenciasUsadas(0) // Inicializamos en 0 explícitamente
                                        .build();

                        List<JugadorPlantel> slots = new ArrayList<>();
                        for (int i = 0; i < request.getJugadores().size(); i++) {
                                GuardarPlantelRequest.SlotJugadorRequest slot = request.getJugadores().get(i);
                                JugadorReal jugador = jugadoresReales.get(i);

                                slots.add(JugadorPlantel.builder()
                                                .plantelJornada(plantelAGuardar)
                                                .jugadorReal(jugador)
                                                .rol(slot.getRol())
                                                .precioDeCompra(jugador.getValorMercadoActual())
                                                .build());
                        }
                        plantelAGuardar.setJugadores(slots);

                        // Descontamos presupuesto SOLO en la creación inicial
                        equipo.setPresupuestoActual(equipo.getPresupuestoActual() - costoTotal);
                        equipoVirtualRepo.save(equipo);

                        // Marcamos como activo
                        onboardingService.marcarActivo(usuarioId);

                        log.info("[PLANTEL] Usuario {} armó plantel inicial. Costo: {}", usuarioId, costoTotal);
                }

                PlantelJornada guardado = plantelRepo.save(plantelAGuardar);
                return toDto(guardado, equipo.getPresupuestoActual());
        }

        // ── Helpers de validación ───────────────────────────────────────────────

        public void validarPlantelPostTraspaso(PlantelJornada plantelActual, List<JugadorReal> salen, List<JugadorReal> entran) {
                // Simulamos el plantel cambiando los jugadores que salen por los que entran
                List<JugadorReal> jugadoresSimulados = new ArrayList<>();
                List<PosicionJugador> posicionesTitulares = new ArrayList<>();
                List<PosicionJugador> posicionesBanco = new ArrayList<>();

                // Obtener IDs de los que salen
                List<Long> salenIds = salen.stream().map(JugadorReal::getId).toList();

                for (JugadorPlantel jp : plantelActual.getJugadores()) {
                        JugadorReal jugadorRealActual = jp.getJugadorReal();
                        int indexSale = salenIds.indexOf(jugadorRealActual.getId());
                        
                        JugadorReal jugadorSimulado = jugadorRealActual;
                        if (indexSale != -1) {
                                jugadorSimulado = entran.get(indexSale);
                        }
                        
                        jugadoresSimulados.add(jugadorSimulado);

                        if (jp.getRol() == RolPlantel.TITULAR || jp.getRol() == RolPlantel.CAPITAN) {
                                posicionesTitulares.add(jugadorSimulado.getPosicion());
                        } else {
                                posicionesBanco.add(jugadorSimulado.getPosicion());
                        }
                }

                validarLimiteJugadoresPorEquipo(jugadoresSimulados);

                // Validar banco
                boolean tieneGuard = posicionesBanco.stream().anyMatch(p -> p == PosicionJugador.BASE || p == PosicionJugador.ESCOLTA);
                boolean tieneForward = posicionesBanco.stream().anyMatch(p -> p == PosicionJugador.ALERO || p == PosicionJugador.ALA_PIVOT);
                boolean tieneCenter = posicionesBanco.stream().anyMatch(p -> p == PosicionJugador.PIVOT);

                if (!tieneGuard || !tieneForward || !tieneCenter) {
                        throw new FormacionInvalidaException("El traspaso dejaría el banco inválido (se requiere mínimo 1 Base/Escolta, 1 Alero/Ala-Pivot, y 1 Pivot en el banco).");
                }

                // Validar titulares
                if (!FormacionValidator.esValida(plantelActual.getFormacion(), posicionesTitulares)) {
                        throw new FormacionInvalidaException("El traspaso rompería la formación titular (" + plantelActual.getFormacion() + ").");
                }
        }

        private void validarRolesUnicos(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots) {

                long capitanes = slots.stream()
                                .filter(s -> s.getRol() == RolPlantel.CAPITAN).count();
                long sextosHombre = slots.stream()
                                .filter(s -> s.getRol() == RolPlantel.SEXTO_HOMBRE).count();

                if (capitanes != 1) {
                        throw new IllegalArgumentException(
                                        "El plantel debe tener exactamente 1 Capitán.");
                }
                if (sextosHombre != 1) {
                        throw new IllegalArgumentException(
                                        "El plantel debe tener exactamente 1 Sexto Hombre.");
                }
        }

        private void validarLimiteJugadoresPorEquipo(List<JugadorReal> jugadores) {
                java.util.Map<Long, Long> conteoPorEquipo = jugadores.stream()
                                .collect(java.util.stream.Collectors.groupingBy(j -> j.getEquipoReal().getId(), java.util.stream.Collectors.counting()));

                for (java.util.Map.Entry<Long, Long> entry : conteoPorEquipo.entrySet()) {
                        if (entry.getValue() > 2) {
                                String nombreEquipo = jugadores.stream()
                                                .filter(j -> j.getEquipoReal().getId().equals(entry.getKey()))
                                                .findFirst()
                                                .map(j -> j.getEquipoReal().getNombre())
                                                .orElse("Desconocido");
                                throw new IllegalArgumentException(
                                                "No podés tener más de 2 jugadores del mismo equipo (" + nombreEquipo + ").");
                        }
                }
        }

        private List<JugadorReal> cargarJugadores(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots) {

                return slots.stream()
                                .map(slot -> jugadorRepo.findById(slot.getJugadorRealId())
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Jugador no encontrado: " + slot.getJugadorRealId())))
                                .toList();
        }

        private List<PosicionJugador> obtenerPosicionesTitulares(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots,
                        List<JugadorReal> jugadores) {

                List<PosicionJugador> posiciones = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                        RolPlantel rol = slots.get(i).getRol();
                        if (rol == RolPlantel.TITULAR || rol == RolPlantel.CAPITAN) {
                                posiciones.add(jugadores.get(i).getPosicion());
                        }
                }
                return posiciones;
        }

        private double calcularCostoTotal(List<JugadorReal> jugadores) {
                return jugadores.stream()
                                .mapToDouble(JugadorReal::getValorMercadoActual)
                                .sum();
        }

        private double obtenerPresupuesto(Long usuarioId) {
                return equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .map(EquipoVirtual::getPresupuestoActual)
                                .orElse(0.0);
        }

        // ── Mapper ──────────────────────────────────────────────────────────────

        public PlantelDto toDto(PlantelJornada plantel, double presupuestoRestante) {

                List<PlantelDto.JugadorPlantelDto> jugadoresDto = plantel.getJugadores()
                                .stream()
                                .map(jp -> PlantelDto.JugadorPlantelDto.builder()
                                                .jugadorPlantelId(jp.getId())
                                                .jugadorRealId(jp.getJugadorReal().getId())
                                                .nombreCompleto(jp.getJugadorReal().getNombreCompleto())
                                                .numeroCamiseta(jp.getJugadorReal().getNumeroCamiseta())
                                                .equipoSigla(jp.getJugadorReal().getEquipoReal().getSigla())
                                                .colorPrincipal(jp.getJugadorReal().getEquipoReal().getColorPrincipal())
                                                .colorSecundario(jp.getJugadorReal().getEquipoReal()
                                                                .getColorSecundario())
                                                .modeloCamiseta(jp.getJugadorReal().getEquipoReal().getModeloCamiseta())
                                                .posicion(jp.getJugadorReal().getPosicion())
                                                .estado(jp.getJugadorReal().getEstado())
                                                .rol(jp.getRol())
                                                .multiplicador(jp.getMultiplicador())
                                                .precioDeCompra(jp.getPrecioDeCompra())
                                                .valorMercadoActual(jp.getJugadorReal().getValorMercadoActual())
                                                .build())
                                .toList();

                PlantelDto.DtDto dtDto = plantel.getDt() != null
                                ? PlantelDto.DtDto.builder()
                                                .dtId(plantel.getDt().getId())
                                                .nombreCompleto(plantel.getDt().getNombreCompleto())
                                                .nacionalidad(plantel.getDt().getNacionalidad())
                                                .equipoNombre(plantel.getDt().getEquipoReal().getNombre())
                                                .equipoSigla(plantel.getDt().getEquipoReal().getSigla())
                                                .colorPrincipal(plantel.getDt().getEquipoReal().getColorPrincipal())
                                                .colorSecundario(plantel.getDt().getEquipoReal().getColorSecundario())
                                                .estado(plantel.getDt().getEstado())
                                                .build()
                                : null;

                return PlantelDto.builder()
                                .plantelId(plantel.getId())
                                .jornadaNumero(plantel.getJornada().getNumero())
                                .formacion(plantel.getFormacion())
                                .puntajeObtenidoFecha(plantel.getPuntajeObtenidoFecha())
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .dt(dtDto)
                                .jugadores(jugadoresDto)
                                .presupuestoRestante(presupuestoRestante)
                                // ---> ¡ACÁ SE AGREGA LA LÍNEA NUEVA! <---
                                .nombreEquipo(plantel.getUsuario() != null
                                                ? equipoVirtualRepo
                                                                .findByUsuario_Id(plantel.getUsuario().getId())
                                                                .map(EquipoVirtual::getNombre)
                                                                .orElse("Mi Equipo")
                                                : "Mi Equipo")
                                .puntajeDt(calcularPuntajeDtDelPlantel(plantel))
                                .build();
        }

        // ── Transferencias ──────────────────────────────────────────────────────────

        /**
         * Reemplaza un jugador del plantel por otro del mercado.
         *
         * Reglas del PRD:
         * - Máximo 3 transferencias por jornada
         * - No se puede transferir con la jornada EN_JUEGO
         * - Cambiar el DT consume 1 transferencia
         * - El presupuesto se ajusta por la diferencia de precios
         * - El jugador entrante se compra al precio actual del mercado
         * - El jugador saliente se "vende" al precio que fue comprado
         */
        @Transactional
        public TransferenciaResultadoDto realizarTransferencia(
                        Long usuarioId,
                        TransferenciaRequest request) {

                // ── 1. Verificar que la jornada permita cambios ──────────────────────
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(JornadaEnJuegoException::new);

                // ── 2. Cargar plantel activo ─────────────────────────────────────────
                PlantelJornada plantel;
                if (request.getTorneoId() != null) {
                        plantel = plantelRepo
                                        .findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornada.getId(), request.getTorneoId())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No tenés plantel de draft para este torneo."));
                        
                        // Si es Draft, validar que la fase permita transferencias libres
                        if (com.fantasy.lnb.feature.mercado.WaiverService.esFaseRestringida(jornada)) {
                                throw new IllegalStateException("El mercado se encuentra en Fase Restringida. Debés usar los Waivers.");
                        }
                } else {
                        plantel = plantelRepo
                                        .findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornada.getId())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No tenés plantel armado para la jornada activa."));
                }

                // ── 3. Verificar transferencias disponibles ──────────────────────────
                if (!plantel.puedeHacerTransferencia()) {
                        throw new TransferenciasAgotadasException(request.getTorneoId() != null ? 4 : 3);
                }

                // ── 4. Localizar el slot del jugador que sale ────────────────────────
                JugadorPlantel slotSaliente = plantel.getJugadores().stream()
                                .filter(jp -> jp.getJugadorReal().getId().equals(request.getJugadorSaleId()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "El jugador que sale no está en tu plantel."));

                // ── 5. Cargar el jugador entrante ────────────────────────────────────
                JugadorReal jugadorEntra = jugadorRepo
                                .findById(request.getJugadorEntraId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Jugador entrante no encontrado: " + request.getJugadorEntraId()));

                // ── 6. Validar que el entrante no esté ya en el plantel ──────────────
                boolean yaEstaEnPlantel = plantel.getJugadores().stream()
                                .anyMatch(jp -> jp.getJugadorReal().getId()
                                                .equals(request.getJugadorEntraId()));
                if (yaEstaEnPlantel) {
                        throw new IllegalArgumentException(
                                        jugadorEntra.getNombreCompleto() + " ya está en tu plantel.");
                }

                // ── 7. Ajuste de presupuesto ─────────────────────────────────────────
                // Se recupera lo que se pagó al comprar al saliente (precio congelado)
                // Se descuenta el precio actual del entrante
                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Equipo virtual no encontrado."));

                double precioVenta = slotSaliente.getPrecioDeCompra();
                double precioCompra = 0.0;
                double diferencia = 0.0;
                double nuevoPresupuesto = equipo.getPresupuestoActual();
                
                if (request.getTorneoId() == null) {
                        precioCompra = jugadorEntra.getValorMercadoActual();
                        diferencia = precioVenta - precioCompra; // Positivo = ganó créditos
                        nuevoPresupuesto = equipo.getPresupuestoActual() + diferencia;
                        
                        if (nuevoPresupuesto < 0) {
                                throw new PresupuestoInsuficienteException(
                                                equipo.getPresupuestoActual(), precioCompra);
                        }
                }

                // ── 8. Ejecutar la transferencia ─────────────────────────────────────
                String nombreSale = slotSaliente.getJugadorReal().getNombreCompleto();
                String nombreEntra = jugadorEntra.getNombreCompleto();
                
                // Guardamos el jugador saliente original para la transaccion del historial
                JugadorReal jugadorSaleOriginal = slotSaliente.getJugadorReal();

                // 1ro: Hacemos el cambio temporal en la memoria
                slotSaliente.setJugadorReal(jugadorEntra);
                slotSaliente.setRol(request.getRolEntrante());
                slotSaliente.setPrecioDeCompra(precioCompra); // Congelar nuevo precio

                // 2do: AHORA validamos que la nueva formación no rompa la táctica elegida
                List<PosicionJugador> posicionesTitulares = plantel.getJugadores().stream()
                                .filter(jp -> jp.getRol() == RolPlantel.TITULAR || jp.getRol() == RolPlantel.CAPITAN)
                                .map(jp -> jp.getJugadorReal().getPosicion())
                                .toList();

                if (!FormacionValidator.esValida(plantel.getFormacion(), posicionesTitulares)) {
                        throw new FormacionInvalidaException(plantel.getFormacion());
                }

                validarLimiteJugadoresPorEquipo(plantel.getJugadores().stream()
                                .map(JugadorPlantel::getJugadorReal)
                                .toList());

                // Si pasó la validación, persistimos en la base de datos
                jugadorPlantelRepo.save(slotSaliente);

                // ── 9. Actualizar contadores ─────────────────────────────────────────
                plantel.setTransferenciasUsadas(plantel.getTransferenciasUsadas() + 1);
                plantelRepo.save(plantel);

                if (request.getTorneoId() == null) {
                        equipo.setPresupuestoActual(nuevoPresupuesto);
                        equipoVirtualRepo.save(equipo);
                } else {
                        // Guardar la transacción de agencia libre en Draft
                        com.fantasy.lnb.feature.mercado.TransaccionDraft transaccion = com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                                        .torneo(plantel.getTorneo())
                                        .usuario(equipo.getUsuario())
                                        .jornada(jornada)
                                        .jugadorEntra(jugadorEntra)
                                        .jugadorSale(jugadorSaleOriginal)
                                        .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.AGENTE_LIBRE)
                                        .build();
                        transaccionDraftRepo.save(transaccion);
                        traspasoUsuarioService.cancelarPropuestasConJugador(jugadorSaleOriginal.getId());
                }

                log.info("[TRANSFERENCIA] Usuario {} | {} → {} | Diferencia: {} | " +
                                "Transferencias usadas: {}/3",
                                usuarioId, nombreSale, nombreEntra,
                                String.format("%.2f", diferencia),
                                plantel.getTransferenciasUsadas());

                return TransferenciaResultadoDto.builder()
                                .jugadorSaleNombre(nombreSale)
                                .jugadorEntraNombre(nombreEntra)
                                .diferenciaPresupuesto(Math.round(diferencia * 100.0) / 100.0)
                                .presupuestoRestante(Math.round(nuevoPresupuesto * 100.0) / 100.0)
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .build();
        }

        /**
         * Cambia el DT del plantel activo.
         * Consume 1 transferencia, igual que cambiar un jugador de campo.
         */
        @Transactional
        public TransferenciaResultadoDto cambiarDt(Long usuarioId, Long nuevoDtId, Long torneoId) {

                // 1. Verificar jornada abierta
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(JornadaEnJuegoException::new);

                // 2. Cargar plantel
                PlantelJornada plantel;
                if (torneoId != null) {
                    plantel = plantelRepo
                                .findByUsuario_IdAndJornada_IdAndTorneo_Id(usuarioId, jornada.getId(), torneoId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "No tenés plantel armado para la jornada activa en este torneo."));
                } else {
                    plantel = plantelRepo
                                .findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornada.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No tenés plantel armado para la jornada activa."));
                }

                // 3. Verificar transferencias
                if (!plantel.puedeHacerTransferencia()) {
                        throw new TransferenciasAgotadasException(torneoId != null ? 4 : 3);
                }

                // 4. Cargar nuevo DT
                DirectorTecnico nuevoDt = dtRepo.findById(nuevoDtId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "DT no encontrado."));
                                                
                if (torneoId != null) {
                        boolean ocupado = plantelRepo.existsByTorneo_IdAndJornada_IdAndDt_Id(torneoId, jornada.getId(), nuevoDtId);
                        if (ocupado) {
                                throw new IllegalStateException("El DT ya fue elegido por otro equipo en esta liga Draft.");
                        }
                }

                String nombreDtSale = plantel.getDt() != null
                                ? plantel.getDt().getNombreCompleto()
                                : "Sin DT";
                String nombreDtEntra = nuevoDt.getNombreCompleto();

                DirectorTecnico dtSale = plantel.getDt();
                
                // ── 5. El DT no tiene precio de mercado — no afecta presupuesto ──────
                // Solo consume la transferencia
                plantel.setDt(nuevoDt);
                plantel.setTransferenciasUsadas(plantel.getTransferenciasUsadas() + 1);
                plantelRepo.save(plantel);

                if (torneoId != null) {
                        com.fantasy.lnb.feature.mercado.TransaccionDraft transaccion = com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                                        .torneo(plantel.getTorneo())
                                        .usuario(plantel.getUsuario())
                                        .jornada(jornada)
                                        .dtEntra(nuevoDt)
                                        .dtSale(dtSale)
                                        .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.AGENTE_LIBRE)
                                        .build();
                        transaccionDraftRepo.save(transaccion);
                        traspasoUsuarioService.cancelarPropuestasConDt(dtSale.getId());
                }

                double presupuestoActual = obtenerPresupuesto(usuarioId);

                log.info("[TRANSFERENCIA-DT] Usuario {} | {} → {} | Transferencias usadas: {}/3",
                                usuarioId, nombreDtSale, nombreDtEntra,
                                plantel.getTransferenciasUsadas());

                return TransferenciaResultadoDto.builder()
                                .jugadorSaleNombre(nombreDtSale)
                                .jugadorEntraNombre(nombreDtEntra)
                                .diferenciaPresupuesto(0.0) // DT no tiene precio
                                .presupuestoRestante(presupuestoActual)
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .build();
        }

        /**
         * Clona el plantel de la jornada anterior a la jornada activa.
         * Se llama desde obtenerPlantelActivo() cuando no existe plantel
         * para la jornada activa pero sí existe uno de la jornada anterior.
         *
         * Copia: jugadores, roles (incluye capitán), formación, DT.
         * Resetea: transferenciasUsadas → 0, puntajeObtenidoFecha → 0.
         */
        @Transactional
        public PlantelJornada clonarPlantelAnterior(
                        Usuario usuario,
                        Jornada jornadaAnterior,
                        Jornada jornadaNueva) {

                PlantelJornada anterior = plantelRepo
                                .findByUsuario_IdAndJornada_IdAndTorneoIsNull(
                                                usuario.getId(), jornadaAnterior.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No existe plantel anterior para clonar."));

                // Construir el nuevo plantel
                PlantelJornada nuevo = PlantelJornada.builder()
                                .usuario(usuario)
                                .jornada(jornadaNueva)
                                .dt(anterior.getDt())
                                .formacion(anterior.getFormacion())
                                .puntajeObtenidoFecha(0.0)
                                .transferenciasUsadas(0) // ← Reset
                                .build();

                // Clonar cada jugador con su rol exacto (capitán incluido)
                List<JugadorPlantel> slots = anterior.getJugadores().stream()
                                .map(jp -> JugadorPlantel.builder()
                                                .plantelJornada(nuevo)
                                                .jugadorReal(jp.getJugadorReal())
                                                .rol(jp.getRol()) // Preserva CAPITAN
                                                .precioDeCompra(jp.getPrecioDeCompra())
                                                .build())
                                .collect(java.util.stream.Collectors.toList());

                nuevo.setJugadores(slots);
                PlantelJornada guardado = plantelRepo.save(nuevo);

                log.info("[PLANTEL] Plantel clonado: Usuario {} | J{} → J{}",
                                usuario.getEmail(),
                                jornadaAnterior.getNumero(),
                                jornadaNueva.getNumero());

                return guardado;
        }

        /**
         * Valida que el banco tenga al menos 1 jugador por cada zona.
         * Regla: mínimo 1 GUARD, 1 FORWARD, 1 CENTER entre los 5 suplentes.
         */
        private void validarComposicionBanco(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots,
                        List<JugadorReal> jugadores) {

                // Filtrar solo los slots del banco
                List<PosicionJugador> posicionesBanco = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                        GuardarPlantelRequest.SlotJugadorRequest slot = slots.get(i);
                        if (slot.getRol() == RolPlantel.SEXTO_HOMBRE ||
                                        slot.getRol() == RolPlantel.SUPLENTE) {
                                posicionesBanco.add(jugadores.get(i).getPosicion());
                        }
                }

                boolean tieneGuard = posicionesBanco.stream()
                                .anyMatch(p -> p == PosicionJugador.BASE || p == PosicionJugador.ESCOLTA);
                boolean tieneForward = posicionesBanco.stream()
                                .anyMatch(p -> p == PosicionJugador.ALERO || p == PosicionJugador.ALA_PIVOT);
                boolean tieneCenter = posicionesBanco.stream().anyMatch(p -> p == PosicionJugador.PIVOT);

                if (!tieneGuard || !tieneForward || !tieneCenter) {
                        List<String> faltantes = new ArrayList<>();
                        if (!tieneGuard)
                                faltantes.add("un Base o Escolta");
                        if (!tieneForward)
                                faltantes.add("un Alero o Ala-Pivot");
                        if (!tieneCenter)
                                faltantes.add("un Pivot");

                        throw new FormacionInvalidaException(
                                        "El banco debe tener al menos: " +
                                                        String.join(", ", faltantes) + ".");
                }
        }

        /**
         * Reconstruye el puntaje del DT buscando el partido de su equipo
         * en la jornada del plantel.
         * Devuelve null si la jornada está abierta o no hay datos del partido.
         * REGLA: Si el equipo juega múltiples veces, solo se evalúa el PRIMER partido.
         */
        private Double calcularPuntajeDtDelPlantel(PlantelJornada plantel) {
                if (plantel.getDt() == null)
                        return null;
                if (plantel.getJornada().estaAbierta())
                        return null;

                Long equipoDtId = plantel.getDt().getEquipoReal().getId();
                Long jornadaId = plantel.getJornada().getId();

                List<Partido> partidos = partidoRepo.findByJornadaIdAndEquipoId(jornadaId, equipoDtId);

                if (partidos.isEmpty())
                        return null;

                partidos.sort(Comparator.comparing(Partido::getFechaHora));
                Partido primerPartido = partidos.get(0);

                if (primerPartido.getPuntosLocal() == null || primerPartido.getPuntosVisitante() == null)
                        return null;

                // Corregimos la lógica: primero identificamos si el equipo del DT es local o
                // visitante
                boolean esLocal = primerPartido.getEquipoLocal().getId().equals(equipoDtId);

                int puntosEquipoDt = esLocal ? primerPartido.getPuntosLocal() : primerPartido.getPuntosVisitante();
                int puntosRival = esLocal ? primerPartido.getPuntosVisitante() : primerPartido.getPuntosLocal();

                return motorPuntuacionPlantel.calcularPuntajeDt(puntosEquipoDt, puntosRival);
        }
}