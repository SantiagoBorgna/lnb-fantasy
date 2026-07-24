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
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/test/simulador")
@RequiredArgsConstructor
// ¡Clave! Esto hace que el endpoint solo exista si tu application.properties NO
// está en prod
@Profile("!prod")
public class TestSimuladorController {

    private final PartidoRepository partidoRepo;
    private final JugadorRealRepository jugadorRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final EquipoRealRepository equipoRepo;
    private final JornadaRepository jornadaRepo;
    private final DataSeederService dataSeederService;
    private final org.springframework.context.ApplicationContext context;

    // Si tu motor de puntuacin tiene un mǸtodo para calcular la stat individual,
    // lo inyectamos.
    // Si la entidad EstadisticaPartido lo calcula sola internamente, podǸs borrar
    // esto.
    private final MotorPuntuacionPlantel motorPuntuacion;

    @GetMapping("/seed-environment")
    public String seedEnvironment() {
        return dataSeederService.seedEnvironment();
    }

    @PostMapping("/jornada/{jornadaId}/procesar")
    public String procesarJornada(@PathVariable Long jornadaId) {
        try {
            com.fantasy.lnb.feature.plantel.PuntuacionService ps = context.getBean(com.fantasy.lnb.feature.plantel.PuntuacionService.class);
            ps.calcularPuntajesDeJornada(jornadaId, true);
            return "Jornada procesada";
        } catch (Exception e) {
            log.error("Error al procesar jornada", e);
            return "Error: " + e.getMessage() + " | Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "null");
        }
    }

    @PostMapping("/jornada/{jornadaId}")
    public String simularJornada(@PathVariable Long jornadaId) {
        Jornada jornada = jornadaRepo.findById(jornadaId)
                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada"));

        List<Partido> partidos = partidoRepo.findByJornada_Id(jornadaId);

        // 1. Si no hay partidos, armamos el fixture de mentira mezclando los equipos
        if (partidos.isEmpty()) {
            List<EquipoReal> equipos = equipoRepo.findAll();
            Collections.shuffle(equipos); // Los mezclamos al azar

            // Agrupamos de a 2. Si son 19, el último queda libre.
            for (int i = 0; i < equipos.size() - 1; i += 2) {
                Partido p = Partido.builder()
                        .jornada(jornada)
                        .equipoLocal(equipos.get(i))
                        .equipoVisitante(equipos.get(i + 1))
                        .estado(EstadoPartido.PROGRAMADO)
                        .fechaHora(LocalDateTime.now())
                        .creadoEn(LocalDateTime.now())
                        .gesHash("SIM-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                        .gesUrl("https://simulacion.lnb.com/" + i)
                        .estadisticasProcesadas(false)
                        .puntosLocal(0)
                        .puntosVisitante(0)
                        .build();
                partidos.add(p);
            }
            partidoRepo.saveAll(partidos);
            log.info("[SIMULADOR] Se crearon {} partidos al azar.", partidos.size());
        }

        Random rnd = new Random();
        int statsGeneradas = 0;

        for (Partido partido : partidos) {
            // 2. Inventar un resultado realista de LNB
            partido.setPuntosLocal(70 + rnd.nextInt(31));
            partido.setPuntosVisitante(70 + rnd.nextInt(31));

            if (partido.getPuntosLocal().equals(partido.getPuntosVisitante())) {
                partido.setPuntosLocal(partido.getPuntosLocal() + 1);
            }

            partido.setEstado(EstadoPartido.FINALIZADO);
            partidoRepo.save(partido);

            // 3. Generar estadísticas
            statsGeneradas += simularEquipo(partido.getEquipoLocal().getId(), partido, true, rnd);
            statsGeneradas += simularEquipo(partido.getEquipoVisitante().getId(), partido, false, rnd);
        }

        // 4. Clave para el DT: cerramos la jornada para que deje de estar "ABIERTA"
        jornada.setEstado(EstadoJornada.FINALIZADA);
        jornadaRepo.save(jornada);

        log.info("[SIMULADOR] Jornada {} simulada. Partidos: {}. Stats: {}", jornadaId, partidos.size(),
                statsGeneradas);
        return "¡Simulación exitosa! Partidos actualizados: " + partidos.size() + " | Stats creadas: " + statsGeneradas;
    }

    private int simularEquipo(Long equipoId, Partido partido, boolean esLocal, Random rnd) {
        // Asumo que tenés un método en el repo para buscar jugadores por ID de su
        // equipo real
        List<JugadorReal> jugadores = jugadorRepo.findByEquipoReal_Id(equipoId);
        List<EstadisticaPartido> statsNuevas = new ArrayList<>();

        boolean equipoGano = esLocal
                ? partido.getPuntosLocal() > partido.getPuntosVisitante()
                : partido.getPuntosVisitante() > partido.getPuntosLocal();

        for (JugadorReal jugador : jugadores) {

            if (estadisticaRepo.findByJugadorReal_IdAndJornada_Id(jugador.getId(), partido.getJornada().getId())
                    .isPresent()) {
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
                    .gesPartidoId("8598544")
                    .puntajeFantasyCalculado(Double.valueOf(rnd.nextInt(200)))
                    .build();

            // NOTA: Si tu entidad EstadisticaPartido no calcula su
            // 'puntajeFantasyCalculado'
            // automáticamente adentro de un @PrePersist, tenés que setearlo acá usando tu
            // motor.

            statsNuevas.add(stat);
        }

        estadisticaRepo.saveAll(statsNuevas);
        return statsNuevas.size();
    }
}