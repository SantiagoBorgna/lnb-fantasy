package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.scraper.FixtureComparador.DiferenciaFixture;
import com.fantasy.lnb.scraper.FixtureCrawlerService.PartidoPublicado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Busca diferencias entre el fixture de la liga y los partidos cargados en las
 * jornadas que todavía no terminaron. Solo lee: corregirlas es decisión del admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureControlService {

    private final JornadaRepository jornadaRepo;
    private final PartidoRepository partidoRepo;
    private final FixtureCrawlerService crawler;

    /**
     * @param error descripción del problema si no se pudo hacer el control; en ese
     *              caso la lista vacía no significa que no haya diferencias
     */
    public record ResultadoControl(LocalDateTime fechaControl, String error, List<Integer> jornadas,
            LocalDate desde, LocalDate hasta, List<DiferenciaFixture> diferencias) {

        public boolean exitoso() {
            return error == null;
        }
    }

    public ResultadoControl controlar() {
        List<Jornada> jornadas = jornadaRepo.findAll();

        List<Long> sinFinalizar = jornadas.stream()
                .filter(j -> !j.estaFinalizada())
                .map(Jornada::getId)
                .toList();

        List<Partido> controlados = sinFinalizar.isEmpty()
                ? List.of()
                : partidoRepo.findConDetalleByJornadaIds(sinFinalizar);

        if (controlados.isEmpty()) {
            return new ResultadoControl(LocalDateTime.now(), null, List.of(), null, null, List.of());
        }

        Set<Long> idsControlados = controlados.stream()
                .map(p -> p.getJornada().getId())
                .collect(Collectors.toSet());

        List<Jornada> jornadasControladas = jornadas.stream()
                .filter(j -> idsControlados.contains(j.getId()))
                .sorted(Comparator.comparing(Jornada::getNumero))
                .toList();
        List<Integer> numeros = jornadasControladas.stream().map(Jornada::getNumero).toList();

        // Desde el día anterior: la liga no incluye las 00:00 del primer día del rango,
        // que es como publica los partidos con la hora pendiente.
        LocalDate desde = Stream.concat(
                        jornadasControladas.stream().map(Jornada::getFechaInicio),
                        controlados.stream().map(Partido::getFechaHora))
                .min(Comparator.naturalOrder()).orElseThrow()
                .toLocalDate().minusDays(1);
        LocalDate hasta = Stream.concat(
                        jornadasControladas.stream().map(Jornada::getFechaFin),
                        controlados.stream().map(Partido::getFechaHora))
                .max(Comparator.naturalOrder()).orElseThrow()
                .toLocalDate();

        List<PartidoPublicado> publicados;
        try {
            publicados = crawler.leerFixture(desde, hasta);
        } catch (Exception e) {
            log.warn("[CONTROL-FIXTURE] No se pudo leer el fixture de la liga: {}", e.getMessage());
            return new ResultadoControl(LocalDateTime.now(), "No se pudo leer el fixture de la liga: " + e.getMessage(),
                    numeros, desde, hasta, List.of());
        }

        // Si no se entendió ninguna fila, lo más probable es que la página haya cambiado.
        // Compararla igual daría por desaparecidos todos los partidos.
        boolean algunaLegible = publicados.stream()
                .anyMatch(p -> p.local() != null && p.visitante() != null && p.fechaHora() != null);
        if (!algunaLegible) {
            log.warn("[CONTROL-FIXTURE] La liga devolvió {} fila(s) y ninguna se pudo interpretar.", publicados.size());
            return new ResultadoControl(LocalDateTime.now(),
                    "La liga no devolvió partidos que se puedan interpretar: puede haber cambiado su página.",
                    numeros, desde, hasta, List.of());
        }

        // La liga también incluye las 00:00 del día siguiente a hasta: quedan afuera.
        List<PartidoPublicado> enRango = publicados.stream()
                .filter(p -> p.fechaHora() == null || !p.fechaHora().toLocalDate().isAfter(hasta))
                .toList();

        Map<Long, Partido> partidos = new LinkedHashMap<>();
        controlados.forEach(p -> partidos.put(p.getId(), p));
        partidoRepo.findConDetalleEntreFechas(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())
                .forEach(p -> partidos.putIfAbsent(p.getId(), p));

        List<DiferenciaFixture> diferencias = new ArrayList<>(FixtureComparador.comparar(
                enRango, partidos.values(), jornadas, idsControlados));

        return new ResultadoControl(LocalDateTime.now(), null, numeros, desde, hasta, diferencias);
    }
}
