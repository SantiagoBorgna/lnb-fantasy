package com.fantasy.lnb.feature.test;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.torneo.*;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSeederService {

    private final UsuarioRepository usuarioRepo;
    private final TorneoRepository torneoRepo;
    private final TorneoEquipoRepository torneoEquipoRepo;
    private final JornadaRepository jornadaRepo;
    private final PartidoRepository partidoRepo;
    private final EquipoRealRepository equipoRepo;
    private final JugadorRealRepository jugadorRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final EquipoVirtualRepository equipoVirtualRepo;

    @Transactional
    public String seedEnvironment() {
        log.info("[SEEDER] Iniciando proceso de data seeding...");
        
        // 1. Crear usuarios bots
        List<Usuario> bots = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            final String email = "bot" + i + "@fantasy.com";
            final String botName = "Bot " + i;
            final String providerId = "bot-" + i;
            Usuario bot = usuarioRepo.findByEmail(email).orElseGet(() -> {
                Usuario nuevo = Usuario.builder()
                        .email(email)
                        .nombreDisplay(botName)
                        .provider("bot")
                        .providerId(providerId)
                        .build();
                return usuarioRepo.save(nuevo);
            });
            bots.add(bot);
        }
        log.info("[SEEDER] {} bots asegurados.", bots.size());

        // 2. Unir bots a los torneos que no estén llenos
        List<Torneo> torneosActivos = torneoRepo.findAll();
        for (Torneo t : torneosActivos) {
            int faltantes = t.getMaxParticipantes() != null ? t.getMaxParticipantes() - t.getParticipantes().size() : 7;
            if (faltantes > 0) {
                for (int i = 0; i < Math.min(faltantes, bots.size()); i++) {
                    Usuario bot = bots.get(i);
                    boolean yaParticipa = t.getParticipantes().stream().anyMatch(te -> te.getEquipoVirtual().getUsuario().getId().equals(bot.getId()));
                    if (!yaParticipa) {
                        TorneoEquipo te = TorneoEquipo.builder()
                                .torneo(t)
                                .puntajeGlobal(0.0)
                                .partidosGanados(0)
                                .partidosEmpatados(0)
                                .partidosPerdidos(0)
                                .puntosFavor(0.0)
                                .build();
                        EquipoVirtual ev = equipoVirtualRepo.findByUsuario_Id(bot.getId()).orElseGet(() -> {
                            EquipoVirtual nuevoEv = EquipoVirtual.builder()
                                    .usuario(bot)
                                    .nombre("Equipo " + bot.getNombreDisplay())
                                    .presupuestoActual(100.0)
                                    .puntajeGlobal(0.0)
                                    .build();
                            return equipoVirtualRepo.save(nuevoEv);
                        });
                        te.setEquipoVirtual(ev);
                        t.getParticipantes().add(te);
                        torneoEquipoRepo.save(te);
                    }
                }
                torneoRepo.save(t);
                log.info("[SEEDER] Torneo '{}' llenado con bots.", t.getNombre());
            }
        }

        // 3. Crear jornadas pasadas, actuales y futuras
        crearJornadasFicticias();

        // 4. Simular Partidos y Stats para las jornadas finalizadas
        simularStatsHistoriales();

        // 5. Actualizar valores de jugadores (promedios)
        actualizarPromediosJugadores();

        return "Seeding completado exitosamente.";
    }

    private void crearJornadasFicticias() {
        if (jornadaRepo.count() >= 10) return; // Si ya hay varias, no duplicar.
        
        log.info("[SEEDER] Creando 10 jornadas pasadas, 1 actual y 2 futuras...");
        for (int i = 1; i <= 13; i++) {
            EstadoJornada estado;
            LocalDateTime fechaFin;
            if (i <= 10) {
                estado = EstadoJornada.FINALIZADA;
                fechaFin = LocalDateTime.now().minusDays((13 - i) * 3L);
            } else if (i == 11) {
                estado = EstadoJornada.EN_JUEGO;
                fechaFin = LocalDateTime.now().plusDays(2);
            } else {
                estado = EstadoJornada.ABIERTA_A_CAMBIOS;
                fechaFin = LocalDateTime.now().plusDays((i - 10) * 3L);
            }
            
            Jornada j = Jornada.builder()
                    .numero(i)
                    .estado(estado)
                    .fechaInicio(fechaFin.minusDays(2))
                    .fechaFin(fechaFin)
                    .build();
            jornadaRepo.save(j);
        }
    }

    private void simularStatsHistoriales() {
        List<Jornada> pasadas = jornadaRepo.findByEstadoNotOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS);
        List<EquipoReal> equipos = equipoRepo.findAll();
        Random rnd = new Random();

        for (Jornada j : pasadas) {
            if (j.getEstado() == EstadoJornada.EN_JUEGO) continue; // Solo stats a finalizadas
            
            List<Partido> partidos = partidoRepo.findByJornada_Id(j.getId());
            if (partidos.isEmpty()) {
                Collections.shuffle(equipos);
                for (int i = 0; i < equipos.size() - 1; i += 2) {
                    String hash = UUID.randomUUID().toString();
                    Partido p = Partido.builder()
                            .jornada(j)
                            .equipoLocal(equipos.get(i))
                            .equipoVisitante(equipos.get(i + 1))
                            .estado(EstadoPartido.FINALIZADO)
                            .fechaHora(j.getFechaInicio().plusHours(12))
                            .puntosLocal(70 + rnd.nextInt(31))
                            .puntosVisitante(70 + rnd.nextInt(31))
                            .creadoEn(LocalDateTime.now())
                            .estadisticasProcesadas(true)
                            .gesHash(hash)
                            .gesUrl("http://ges.com/partido/" + hash)
                            .build();
                    partidoRepo.save(p);
                    partidos.add(p);
                }
            }

            for (Partido partido : partidos) {
                if (partido.getPuntosLocal().equals(partido.getPuntosVisitante())) {
                    partido.setPuntosLocal(partido.getPuntosLocal() + 1);
                    partidoRepo.save(partido);
                }
                simularEquipo(partido.getEquipoLocal().getId(), partido, true, rnd);
                simularEquipo(partido.getEquipoVisitante().getId(), partido, false, rnd);
            }
        }
    }

    private void simularEquipo(Long equipoId, Partido partido, boolean esLocal, Random rnd) {
        List<JugadorReal> jugadores = jugadorRepo.findByEquipoReal_Id(equipoId);
        List<EstadisticaPartido> statsNuevas = new ArrayList<>();
        boolean equipoGano = esLocal ? partido.getPuntosLocal() > partido.getPuntosVisitante() : partido.getPuntosVisitante() > partido.getPuntosLocal();

        for (JugadorReal jugador : jugadores) {
            if (estadisticaRepo.findByJugadorReal_IdAndJornada_Id(jugador.getId(), partido.getJornada().getId()).isPresent()) {
                continue;
            }
            EstadisticaPartido stat = EstadisticaPartido.builder()
                    .asistencias(rnd.nextInt(8))
                    .creadoEn(LocalDateTime.now())
                    .equipoGano(equipoGano)
                    .faltasCometidas(rnd.nextInt(3))
                    .faltasRecibidas(rnd.nextInt(6))
                    .fechaPartido(LocalDateTime.now())
                    .fueLocal(esLocal)
                    .fueTitularEnPartidoReal(rnd.nextBoolean())
                    .jornada(partido.getJornada())
                    .perdidas(rnd.nextInt(5))
                    .puntos(rnd.nextInt(25))
                    .rebotesDefensivos(rnd.nextInt(8))
                    .rebotesOfensivos(rnd.nextInt(4))
                    .recuperaciones(rnd.nextInt(4))
                    .taponesRealizados(rnd.nextInt(3))
                    .taponesRecibidos(rnd.nextInt(2))
                    .tirosCampoFallados(rnd.nextInt(8))
                    .tirosLibresFallados(rnd.nextInt(4))
                    .jugadorReal(jugador)
                    .fueDescalificado(false)
                    .tieneFaltaTecnica(false)
                    .fueExpulsadoPorFaltas(false)
                    .gesPartidoId(partido.getGesHash())
                    // Valor fantasy calculado a lo bruto para test
                    .puntajeFantasyCalculado(Double.valueOf(rnd.nextInt(30) + 10))
                    .build();
            statsNuevas.add(stat);
        }
        estadisticaRepo.saveAll(statsNuevas);
    }

    private void actualizarPromediosJugadores() {
        List<JugadorReal> todos = jugadorRepo.findAll();
        for (JugadorReal j : todos) {
            List<EstadisticaPartido> stats = estadisticaRepo.findByJugadorReal_Id(j.getId());
            if (!stats.isEmpty()) {
                double suma = stats.stream().mapToDouble(EstadisticaPartido::getPuntajeFantasyCalculado).sum();
                j.setPromedioFantasy(suma / stats.size());
                j.setValorMercadoActual(5.0 + (j.getPromedioFantasy() * 0.2));
                jugadorRepo.save(j);
            }
        }
    }
}
