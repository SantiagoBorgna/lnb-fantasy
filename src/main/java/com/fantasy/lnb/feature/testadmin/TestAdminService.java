package com.fantasy.lnb.feature.testadmin;

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
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestAdminService {

    private final EntityManager entityManager;
    private final JornadaRepository jornadaRepository;
    private final EquipoRealRepository equipoRealRepository;
    private final PartidoRepository partidoRepository;
    private final JugadorRealRepository jugadorRealRepository;
    private final EstadisticaPartidoRepository estadisticaPartidoRepository;

    @Transactional(noRollbackFor = Exception.class)
    public void resetDb() {
        log.warn("ATENCION: Ejecutando borrado total de datos transaccionales.");
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        String[] tables = {
                "transaccion_draft",
                "waiver_claim",
                "propuesta_traspaso",
                "prop_jugadores_ofrecidos",
                "prop_jugadores_solicitados",
                "plantel_jornada_jugadores",
                "plantel_jornada",
                "estadistica_partido",
                "partido",
                "jornada",
                "torneo_equipo",
                "equipo_virtual",
                "torneo",
                "notificacion",
                "usuario"
        };

        for (String table : tables) {
            try {
                entityManager.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate();
            } catch (Exception e) {
                log.warn("Ignorando error al truncar la tabla '{}': {}", table, e.getMessage());
            }
        }

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        log.info("Limpieza completada.");
    }

    @Transactional
    @CacheEvict(value = {"jornadas", "partidos"}, allEntries = true)
    public void seedJornadas() {
        if (jornadaRepository.count() > 0) {
            log.warn("Eliminando jornadas existentes para generar el fixture falso...");
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE plantel_jornada_jugadores").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE plantel_jornada").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE partido").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE jornada").executeUpdate();
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        }

        List<EquipoReal> equipos = equipoRealRepository.findAll();
        if (equipos.size() < 20) {
            log.warn("Se encontraron menos de 20 equipos reales. Total: " + equipos.size());
        }

        LocalDateTime baseDate = LocalDateTime.now().plusDays(1).withHour(20).withMinute(0);

        for (int i = 1; i <= 3; i++) {
            Jornada jornada = Jornada.builder()
                    .numero(i)
                    .fechaInicio(baseDate.plusDays((i - 1) * 7))
                    .fechaFin(baseDate.plusDays((i - 1) * 7).plusDays(3))
                    .estado(EstadoJornada.ABIERTA_A_CAMBIOS)
                    .build();
            jornadaRepository.save(jornada);

            // Mezclar equipos para emparejarlos
            List<EquipoReal> shuffled = new ArrayList<>(equipos);
            Collections.shuffle(shuffled);

            for (int j = 0; j < shuffled.size() - 1; j += 2) {
                EquipoReal local = shuffled.get(j);
                EquipoReal visitante = shuffled.get(j + 1);

                Partido partido = Partido.builder()
                        .jornada(jornada)
                        .equipoLocal(local)
                        .equipoVisitante(visitante)
                        .gesHash("FAKE_HASH_J" + i + "_M" + j)
                        .gesUrl("http://fake.ges.url/J" + i + "M" + j)
                        .fechaHora(jornada.getFechaInicio().plusHours(1))
                        .estado(EstadoPartido.PROGRAMADO)
                        .build();
                partidoRepository.save(partido);
            }
        }
        log.info("3 Jornadas y Partidos ficticios generados.");
    }

    @Transactional
    @CacheEvict(value = {"jornadas", "partidos"}, allEntries = true)
    public void simularJornada(Long jornadaId) {
        Jornada jornada = jornadaRepository.findById(jornadaId)
                .orElseThrow(() -> new IllegalArgumentException("Jornada no existe"));

        if (jornada.getEstado() == EstadoJornada.FINALIZADA) {
            throw new IllegalStateException("La jornada ya est finalizada");
        }

        List<Partido> partidos = partidoRepository.findByJornada_Id(jornadaId);
        Random rand = new Random();

        for (Partido p : partidos) {
            p.setEstado(EstadoPartido.FINALIZADO);
            p.setPuntosLocal(70 + rand.nextInt(40));
            p.setPuntosVisitante(70 + rand.nextInt(40));
            partidoRepository.save(p);

            // Generar stats para ambos equipos
            simularEstadisticasEquipo(p.getEquipoLocal(), jornada, p, true, rand);
            simularEstadisticasEquipo(p.getEquipoVisitante(), jornada, p, false, rand);
        }

        jornada.setEstado(EstadoJornada.FINALIZADA);
        jornadaRepository.save(jornada);
        log.info("Jornada {} simulada. Estadsticas generadas.", jornada.getNumero());
    }

    private void simularEstadisticasEquipo(EquipoReal equipo, Jornada jornada, Partido p, boolean local, Random rand) {
        List<JugadorReal> jugadores = jugadorRealRepository.findByEquipoReal_Id(equipo.getId());

        for (JugadorReal j : jugadores) {
            // Ignorar algunos suplentes
            if (rand.nextDouble() < 0.2) continue;

            int puntos = rand.nextInt(25);
            int rebotesDef = rand.nextInt(8);
            int rebotesOf = rand.nextInt(4);
            int asistencias = rand.nextInt(8);
            int faltasCometidas = rand.nextInt(5);
            int robos = rand.nextInt(3);
            int bloqueos = rand.nextInt(2);
            int perdidas = rand.nextInt(4);

            double fantasyScore = puntos + (rebotesDef * 1.2) + (rebotesOf * 1.5) + (asistencias * 1.5)
                    + (robos * 2) + (bloqueos * 2) - faltasCometidas - perdidas;

            EstadisticaPartido stat = EstadisticaPartido.builder()
                    .jugadorReal(j)
                    .jornada(jornada)
                    .gesPartidoId(p.getGesHash())
                    .fechaPartido(p.getFechaHora())
                    .fueLocal(local)
                    .puntos(puntos)
                    .rebotesDefensivos(rebotesDef)
                    .rebotesOfensivos(rebotesOf)
                    .asistencias(asistencias)
                    .faltasCometidas(faltasCometidas)
                    .faltasRecibidas(rand.nextInt(4))
                    .recuperaciones(robos)
                    .taponesRealizados(bloqueos)
                    .perdidas(perdidas)
                    .tirosCampoFallados(5)
                    .tirosLibresFallados(rand.nextInt(3))
                    .puntajeFantasyCalculado(Math.max(0.0, fantasyScore))
                    .build();
            estadisticaPartidoRepository.save(stat);
        }
    }

    @Transactional
    public void seedPartidosParaJornadasExistentes() {
        List<Jornada> jornadas = jornadaRepository.findAll();
        List<EquipoReal> equipos = equipoRealRepository.findAll();
        
        for (Jornada jornada : jornadas) {
            if (partidoRepository.countByJornada_Id(jornada.getId()) > 0) continue;
            
            List<EquipoReal> shuffled = new ArrayList<>(equipos);
            Collections.shuffle(shuffled);
            
            for (int j = 0; j < shuffled.size() - 1; j += 2) {
                EquipoReal local = shuffled.get(j);
                EquipoReal visitante = shuffled.get(j + 1);
                
                Partido partido = Partido.builder()
                        .jornada(jornada)
                        .equipoLocal(local)
                        .equipoVisitante(visitante)
                        .gesHash("FAKE_HASH_J" + jornada.getNumero() + "_M" + j)
                        .gesUrl("http://fake.ges.url/J" + jornada.getNumero() + "M" + j)
                        .fechaHora(jornada.getFechaInicio().plusHours(1))
                        .estado(EstadoPartido.PROGRAMADO)
                        .build();
                partidoRepository.save(partido);
            }
        }
        log.info("Partidos agregados a las jornadas existentes.");
    }

    @Transactional
    public void seedIndependiente() {
        List<Jornada> jornadas = jornadaRepository.findAllById(List.of(5L, 6L, 7L, 8L));
        List<EquipoReal> equipos = equipoRealRepository.findAll();

        EquipoReal independiente = equipos.stream()
                .filter(e -> e.getNombre().toLowerCase().contains("independiente"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No se encontró a Independiente"));

        for (Jornada jornada : jornadas) {
            if (partidoRepository.countByJornada_Id(jornada.getId()) > 0) continue;

            List<EquipoReal> disponibles = new ArrayList<>(equipos);
            disponibles.remove(independiente);
            Collections.shuffle(disponibles);

            EquipoReal rival = disponibles.remove(0);

            boolean isLocal = (jornada.getId() == 5L || jornada.getId() == 7L);

            Partido partidoIndependiente = Partido.builder()
                    .jornada(jornada)
                    .equipoLocal(isLocal ? independiente : rival)
                    .equipoVisitante(isLocal ? rival : independiente)
                    .gesHash("FAKE_HASH_IND_J" + jornada.getId())
                    .gesUrl("http://fake.ges.url/IND_J" + jornada.getId())
                    .fechaHora(jornada.getFechaInicio() != null ? jornada.getFechaInicio().plusHours(1) : LocalDateTime.now().plusDays(jornada.getId()))
                    .estado(EstadoPartido.PROGRAMADO)
                    .build();
            partidoRepository.save(partidoIndependiente);

            // Crear para el resto
            for (int j = 0; j < disponibles.size() - 1; j += 2) {
                EquipoReal local = disponibles.get(j);
                EquipoReal visitante = disponibles.get(j + 1);

                Partido partido = Partido.builder()
                        .jornada(jornada)
                        .equipoLocal(local)
                        .equipoVisitante(visitante)
                        .gesHash("FAKE_HASH_J" + jornada.getId() + "_M" + j)
                        .gesUrl("http://fake.ges.url/J" + jornada.getId() + "M" + j)
                        .fechaHora(jornada.getFechaInicio() != null ? jornada.getFechaInicio().plusHours(1) : LocalDateTime.now().plusDays(jornada.getId()))
                        .estado(EstadoPartido.PROGRAMADO)
                        .build();
                partidoRepository.save(partido);
            }
        }
        log.info("Partidos agregados a jornadas 5 a 8 con Independiente local en 5, 6 y visitante en 7, 8.");
    }
}

