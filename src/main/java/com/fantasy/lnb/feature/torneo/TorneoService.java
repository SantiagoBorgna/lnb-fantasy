package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.torneo.dto.CrearTorneoRequest;
import com.fantasy.lnb.feature.torneo.dto.EditarTorneoRequest;
import com.fantasy.lnb.feature.torneo.dto.EnfrentamientoDto;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import com.fantasy.lnb.feature.torneo.dto.TorneoDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorneoService {

        private final TorneoRepository torneoRepo;
        private final TorneoEquipoRepository torneoEquipoRepo;
        private final UsuarioRepository usuarioRepo;
        private final EquipoVirtualRepository equipoVirtualRepo;
        private final EnfrentamientoH2HRepository enfrentamientoRepo;
        private final PlantelJornadaRepository plantelRepo;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        // ── Creación ────────────────────────────────────────────────────────────

        @Transactional
        public TorneoDto crearTorneo(Long usuarioId, CrearTorneoRequest request) {

                Usuario creador = usuarioRepo.findById(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Usuario no encontrado: " + usuarioId));

                // Validaciones para Draft
                ModalidadTorneo mod = request.getModalidad() != null ? request.getModalidad() : ModalidadTorneo.CLASICO;
                if (mod == ModalidadTorneo.DRAFT && request.getTipo() == TipoTorneo.PUBLICO) {
                        throw new IllegalArgumentException("Los torneos Draft deben ser Privados.");
                }

                Torneo torneo = Torneo.builder()
                                .nombre(request.getNombre())
                                .descripcion(request.getDescripcion())
                                .tipo(request.getTipo())
                                .modalidad(mod)
                                .tipoPuntuacion(request.getTipoPuntuacion() != null ? request.getTipoPuntuacion() : TipoPuntuacion.GENERAL)
                                .estadoDraft(mod == ModalidadTorneo.DRAFT ? EstadoDraft.PENDIENTE : EstadoDraft.NO_APLICA)
                                .maxParticipantes(mod == ModalidadTorneo.DRAFT ? (request.getMaxParticipantes() != null ? request.getMaxParticipantes() : 8) : null)
                                .codigoInvitacion(UUID.randomUUID().toString())
                                .creador(creador)
                                .build();

                Torneo guardado = torneoRepo.save(torneo);

                // El creador se une automáticamente a su propio torneo
                unirseATorneo(usuarioId, guardado.getCodigoInvitacion());

                log.info("[TORNEO] Creado: '{}' | Tipo: {} | Creador: {} | UUID: {}",
                                guardado.getNombre(), guardado.getTipo(),
                                creador.getEmail(), guardado.getCodigoInvitacion());

                return toDto(guardado);
        }

        // ── Unirse a un torneo ──────────────────────────────────────────────────

        @Transactional
        public TorneoDto unirseATorneo(Long usuarioId, String codigoInvitacion) {

                Torneo torneo = torneoRepo.findByCodigoInvitacion(codigoInvitacion)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Torneo no encontrado con código: " + codigoInvitacion));

                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "El usuario no tiene equipo virtual creado."));

                // Verificar que no esté ya inscripto
                if (torneoEquipoRepo.existsByTorneo_IdAndEquipoVirtual_Id(
                                torneo.getId(), equipo.getId())) {
                        log.warn("[TORNEO] Usuario {} ya está inscripto en torneo {}.",
                                        usuarioId, torneo.getNombre());
                        return toDto(torneo);
                }

                TorneoEquipo inscripcion = TorneoEquipo.builder()
                                .torneo(torneo)
                                .equipoVirtual(equipo)
                                .build();

                torneoEquipoRepo.save(inscripcion);

                log.info("[TORNEO] Usuario {} se unió a '{}'",
                                usuarioId, torneo.getNombre());

                return toDto(torneo);
        }

        @Transactional(readOnly = true)
        public TorneoDto obtenerTorneoPorCodigo(String codigo) {
                Torneo torneo = torneoRepo.findByCodigoInvitacion(codigo)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "El link de invitación no es válido o el torneo no existe."));

                // Devolvemos el DTO (usá el mapper o el armado manual que ya tengas para
                // getTorneo)
                return toDto(torneo);
        }

        // ── Consultas ───────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public List<TorneoDto> listarPublicos(String nombre) {
                if (nombre != null && !nombre.isBlank()) {
                        return torneoRepo
                                        .findByTipoAndNombreContainingIgnoreCase(
                                                        TipoTorneo.PUBLICO, nombre)
                                        .stream().map(this::toDto).toList();
                }
                return torneoRepo.findByTipoOrderByNombreAsc(TipoTorneo.PUBLICO)
                                .stream().map(this::toDto).toList();
        }

        @Transactional(readOnly = true)
        public List<TorneoDto> listarMisTorneos(Long usuarioId) {
                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Equipo virtual no encontrado."));

                return torneoEquipoRepo.findByEquipoVirtual_Id(equipo.getId())
                                .stream()
                                .map(te -> toDto(te.getTorneo()))
                                .toList();
        }

        @Transactional(readOnly = true)
        public Optional<TorneoDto> obtenerPorCodigo(String codigo) {
                return torneoRepo.findByCodigoInvitacion(codigo)
                                .map(this::toDto);
        }

        // ── Tabla de posiciones ─────────────────────────────────────────────────

        public List<PosicionTorneoDto> obtenerTablaPosiciones(Long torneoId) {
                Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
                List<TorneoEquipo> participantes = torneoEquipoRepo.findByTorneo_Id(torneoId);

                // Sort depending on type
                if (torneo.getModalidad() == com.fantasy.lnb.feature.torneo.ModalidadTorneo.DRAFT) {
                    if (torneo.getTipoPuntuacion() == com.fantasy.lnb.feature.torneo.TipoPuntuacion.H2H) {
                        participantes.sort((a, b) -> {
                            int ptsA = a.getPartidosGanados() * 3 + a.getPartidosEmpatados();
                            int ptsB = b.getPartidosGanados() * 3 + b.getPartidosEmpatados();
                            if (ptsA != ptsB) return Integer.compare(ptsB, ptsA);
                            return Double.compare(b.getPuntosFavor(), a.getPuntosFavor());
                        });
                    } else {
                        participantes.sort((a, b) -> Double.compare(b.getPuntajeGlobal(), a.getPuntajeGlobal()));
                    }
                } else {
                    participantes.sort((a, b) -> Double.compare(
                        calcularPuntajeClasicoDinamico(b, torneo.getCreadoEn()), 
                        calcularPuntajeClasicoDinamico(a, torneo.getCreadoEn())
                    ));
                }

                AtomicInteger posicion = new AtomicInteger(1);

                return participantes.stream()
                                .map(te -> PosicionTorneoDto.builder()
                                                .posicion(posicion.getAndIncrement())
                                                .nombreEquipo(te.getEquipoVirtual().getNombre())
                                                .nombreUsuario(te.getEquipoVirtual()
                                                                .getUsuario().getNombreDisplay())
                                                .puntajeGlobal((torneo.getModalidad() == com.fantasy.lnb.feature.torneo.ModalidadTorneo.DRAFT) ? te.getPuntajeGlobal() : calcularPuntajeClasicoDinamico(te, torneo.getCreadoEn()))
                                                .partidosGanados(te.getPartidosGanados())
                                                .partidosEmpatados(te.getPartidosEmpatados())
                                                .partidosPerdidos(te.getPartidosPerdidos())
                                                .puntosFavor(te.getPuntosFavor())
                                                .equipoVirtualId(te.getEquipoVirtual().getId())
                                                .build())
                                .toList();
        }

        private double calcularPuntajeClasicoDinamico(TorneoEquipo te, java.time.LocalDateTime torneoCreadoEn) {
            return plantelRepo.findByUsuario_IdAndTorneoIsNull(te.getEquipoVirtual().getUsuario().getId())
                    .stream()
                    .filter(p -> p.getJornada().getFechaInicio().isAfter(torneoCreadoEn) || p.getJornada().getFechaFin().isAfter(torneoCreadoEn))
                    .mapToDouble(com.fantasy.lnb.feature.plantel.PlantelJornada::getPuntajeObtenidoFecha)
                    .sum();
        }

        // ── Fixture H2H ─────────────────────────────────────────────────────────

        public List<EnfrentamientoDto> obtenerFixtureH2H(Long torneoId) {
                return enfrentamientoRepo.findByTorneo_Id(torneoId).stream()
                        .map(e -> EnfrentamientoDto.builder()
                                .id(e.getId())
                                .jornadaId(e.getJornada().getId())
                                .jornadaNumero(e.getJornada().getNumero())
                                .equipoLocalId(e.getEquipoLocal().getEquipoVirtual().getId())
                                .equipoLocalNombre(e.getEquipoLocal().getEquipoVirtual().getNombre())
                                .equipoVisitanteId(e.getEquipoVisitante() != null ? e.getEquipoVisitante().getEquipoVirtual().getId() : null)
                                .equipoVisitanteNombre(e.getEquipoVisitante() != null ? e.getEquipoVisitante().getEquipoVirtual().getNombre() : null)
                                .puntajeLocal(e.getPuntajeLocal())
                                .puntajeVisitante(e.getPuntajeVisitante())
                                .procesado(e.getProcesado())
                                .build()
                        )
                        .toList();
        }

        // ── Mapper ──────────────────────────────────────────────────────────────

        private TorneoDto toDto(Torneo t) {
                return TorneoDto.builder()
                                .id(t.getId())
                                .nombre(t.getNombre())
                                .descripcion(t.getDescripcion())
                                .tipo(t.getTipo())
                                .modalidad(t.getModalidad())
                                .tipoPuntuacion(t.getTipoPuntuacion())
                                .estadoDraft(t.getEstadoDraft())
                                .maxParticipantes(t.getMaxParticipantes())
                                .codigoInvitacion(t.getCodigoInvitacion())
                                .urlInvitacion(frontendUrl + "/torneos/unirse/"
                                                + t.getCodigoInvitacion())
                                .creadorNombre(t.getCreador().getNombreDisplay())
                                .cantidadParticipantes(t.cantidadParticipantes())
                                .creadoEn(t.getCreadoEn())
                                .build();
        }

        public TorneoDto toDtoConPermisos(Torneo torneo, Long usuarioId) {
                TorneoDto dto = toDto(torneo);
                dto.setEsAdmin(
                                usuarioId != null &&
                                                torneo.getCreador().getId().equals(usuarioId));
                return dto;
        }

        // ── SALIR DE UN TORNEO ─────────────────────────────────────────────
        @Transactional
        public void salirDeTorneo(Long torneoId, Long usuarioId) {
                Torneo torneo = torneoRepo.findById(torneoId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Torneo no encontrado: " + torneoId));

                // El creador no puede salir
                if (torneo.getCreador().getId().equals(usuarioId)) {
                        throw new IllegalStateException(
                                        "El creador no puede salir del torneo. " +
                                                        "Eliminá el torneo desde ajustes.");
                }

                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Equipo virtual no encontrado."));

                torneoEquipoRepo.findAll().stream()
                                .filter(te -> te.getTorneo().getId().equals(torneoId)
                                                && te.getEquipoVirtual().getId().equals(equipo.getId()))
                                .findFirst()
                                .ifPresent(torneoEquipoRepo::delete);

                log.info("[TORNEO] Usuario {} salió del torneo {}", usuarioId, torneoId);
        }

        // ── AGREGAR BOT (TESTING) ──────────────────────────────────────────
        @Transactional
        public TorneoDto agregarBot(Long torneoId, Long adminId) {
                Torneo torneo = torneoRepo.findById(torneoId)
                                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));
                
                if (!torneo.getCreador().getId().equals(adminId)) {
                        throw new IllegalStateException("Solo el administrador puede agregar bots.");
                }

                if (torneo.cantidadParticipantes() >= torneo.getMaxParticipantes()) {
                        throw new IllegalStateException("El torneo ya está lleno.");
                }

                String botEmail = "bot_" + UUID.randomUUID().toString().substring(0, 8) + "@fantasy.com";
                Usuario bot = new Usuario();
                bot.setEmail(botEmail);
                bot.setNombreDisplay("Bot " + UUID.randomUUID().toString().substring(0, 4));
                bot.setProvider("LOCAL");
                bot.setProviderId(botEmail);
                bot.setRol(com.fantasy.lnb.feature.usuario.RolUsuario.USER);
                bot.setEstadoOnboarding(com.fantasy.lnb.feature.usuario.EstadoOnboarding.ACTIVO);
                bot.setUltimoLogin(java.time.LocalDateTime.now());
                bot.setCreadoEn(java.time.LocalDateTime.now());
                usuarioRepo.save(bot);

                EquipoVirtual equipoBot = EquipoVirtual.builder()
                        .usuario(bot)
                        .nombre("Equipo de " + bot.getNombreDisplay())
                        .presupuestoActual(100.0)
                        .puntajeGlobal(0.0)
                        .build();
                equipoVirtualRepo.save(equipoBot);

                return unirseATorneo(bot.getId(), torneo.getCodigoInvitacion());
        }

        // ── Editar datos del torneo ──────────────────────────────────────────────────
        @Transactional
        public TorneoDto editarTorneo(Long torneoId, Long usuarioId,
                        EditarTorneoRequest request) {
                Torneo torneo = torneoRepo.findById(torneoId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Torneo no encontrado: " + torneoId));

                if (!torneo.getCreador().getId().equals(usuarioId)) {
                        throw new IllegalStateException("Solo el admin puede editar el torneo.");
                }

                if (request.getNombre() != null && !request.getNombre().isBlank()) {
                        torneo.setNombre(request.getNombre());
                }
                if (request.getDescripcion() != null) {
                        torneo.setDescripcion(request.getDescripcion());
                }
                if (request.getTipo() != null) {
                        torneo.setTipo(request.getTipo());
                }

                return toDtoConPermisos(torneoRepo.save(torneo), usuarioId);
        }

        // ── Expulsar participante ────────────────────────────────────────────────────
        @Transactional
        public void expulsarParticipante(Long torneoId, Long adminId,
                        Long equipoVirtualId) {
                Torneo torneo = torneoRepo.findById(torneoId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Torneo no encontrado: " + torneoId));

                if (!torneo.getCreador().getId().equals(adminId)) {
                        throw new IllegalStateException("Solo el admin puede expulsar participantes.");
                }

                // No puede expulsarse a sí mismo
                EquipoVirtual equipoAdmin = equipoVirtualRepo
                                .findByUsuario_Id(adminId)
                                .orElseThrow(() -> new IllegalStateException("Equipo admin no encontrado."));

                if (equipoAdmin.getId().equals(equipoVirtualId)) {
                        throw new IllegalStateException(
                                        "El admin no puede expulsarse a sí mismo.");
                }

                torneoEquipoRepo.findAll().stream()
                                .filter(te -> te.getTorneo().getId().equals(torneoId)
                                                && te.getEquipoVirtual().getId().equals(equipoVirtualId))
                                .findFirst()
                                .ifPresent(torneoEquipoRepo::delete);

                log.info("[TORNEO] Admin {} expulsó equipo {} del torneo {}",
                                adminId, equipoVirtualId, torneoId);
        }
}