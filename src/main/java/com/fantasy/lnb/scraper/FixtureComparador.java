package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.scraper.FixtureCrawlerService.PartidoPublicado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compara el fixture que publica la liga con los partidos cargados, sin modificar
 * nada. Un partido se identifica por local + visitante + día, igual que en la
 * sincronización: el hash de GES cambia en cada carga y la hora se puede corregir.
 *
 * Los partidos PROCESADOS no generan avisos porque la sincronización tampoco los
 * cambia. Una jornada sin partidos cargados todavía no se sincronizó, así que lo
 * que la liga publique para esas fechas no es una diferencia.
 */
final class FixtureComparador {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FixtureComparador() {
    }

    enum TipoDiferencia { NUEVO, REPROGRAMADO, NO_PUBLICADO, HORARIO, NO_RECONOCIDO }

    /**
     * @param jornada  número de la jornada del partido, o null si no cae en ninguna
     * @param partidoId partido cargado al que se refiere, si hay uno
     */
    record DiferenciaFixture(TipoDiferencia tipo, String local, String visitante, Integer jornada,
            LocalDateTime fechaBase, LocalDateTime fechaLiga, Long partidoId) {

        /** Identifica la diferencia para avisarla una sola vez. */
        String clave() {
            return tipo + "|" + local + "|" + visitante + "|" + fechaBase + "|" + fechaLiga;
        }

        String descripcion() {
            String partido = local + " vs " + visitante;
            return switch (tipo) {
                case NUEVO -> partido + " (" + fecha(fechaLiga) + "): no está cargado";
                case REPROGRAMADO -> partido + ": pasó del " + fecha(fechaBase) + " al " + fecha(fechaLiga);
                case NO_PUBLICADO -> partido + " (" + fecha(fechaBase) + "): ya no figura en la liga";
                case HORARIO -> partido + ": hora del " + fecha(fechaBase) + " a " + fecha(fechaLiga);
                case NO_RECONOCIDO -> partido + ": no se pudo interpretar la fila de la liga";
            };
        }

        private static String fecha(LocalDateTime fecha) {
            return fecha == null ? "sin fecha" : fecha.format(FORMATO);
        }
    }

    /**
     * @param publicados         lo que publica la liga en el rango controlado
     * @param partidos           partidos cargados en ese rango y los de las jornadas controladas
     * @param jornadas           todas las jornadas
     * @param jornadasControladas ids de las jornadas sin finalizar que tienen partidos cargados
     */
    static List<DiferenciaFixture> comparar(List<PartidoPublicado> publicados, Collection<Partido> partidos,
            List<Jornada> jornadas, Set<Long> jornadasControladas) {

        Map<String, List<Partido>> cargados = partidos.stream()
                .collect(Collectors.groupingBy(FixtureComparador::claveDe));

        Set<String> clavesPublicadas = publicados.stream()
                .filter(FixtureComparador::esLegible)
                .map(FixtureComparador::claveDe)
                .collect(Collectors.toSet());

        // Partidos controlados que la liga ya no publica para ese día
        List<Partido> sinPublicar = new ArrayList<>(partidos.stream()
                .filter(p -> jornadasControladas.contains(p.getJornada().getId()))
                .filter(p -> p.getEstado() != EstadoPartido.PROCESADO)
                .filter(p -> !clavesPublicadas.contains(claveDe(p)))
                .toList());

        List<DiferenciaFixture> diferencias = new ArrayList<>();
        Set<String> vistas = new HashSet<>();

        for (PartidoPublicado publicado : publicados) {
            if (!esLegible(publicado)) {
                diferencias.add(new DiferenciaFixture(TipoDiferencia.NO_RECONOCIDO, publicado.nombreLocal(),
                        publicado.nombreVisitante(), null, null, publicado.fechaHora(), null));
                continue;
            }

            String clave = claveDe(publicado);
            if (!vistas.add(clave)) {
                continue;
            }

            List<Partido> mismoPartido = cargados.get(clave);
            if (mismoPartido != null) {
                revisarHorario(publicado, mismoPartido).ifPresent(diferencias::add);
                continue;
            }

            Optional<Partido> anterior = sinPublicar.stream()
                    .filter(p -> mismosEquipos(p, publicado))
                    .findFirst();

            if (anterior.isPresent()) {
                sinPublicar.remove(anterior.get());
                diferencias.add(diferencia(TipoDiferencia.REPROGRAMADO, anterior.get(), publicado.fechaHora()));
                continue;
            }

            Optional<Jornada> jornada = jornadas.stream()
                    .filter(j -> j.contieneTimestamp(publicado.fechaHora()))
                    .findFirst();

            boolean jornadaSinCargar = jornada
                    .filter(j -> !j.estaFinalizada() && !jornadasControladas.contains(j.getId()))
                    .isPresent();

            if (!jornadaSinCargar) {
                diferencias.add(new DiferenciaFixture(TipoDiferencia.NUEVO, publicado.local().getNombre(),
                        publicado.visitante().getNombre(), jornada.map(Jornada::getNumero).orElse(null),
                        null, publicado.fechaHora(), null));
            }
        }

        sinPublicar.forEach(p -> diferencias.add(diferencia(TipoDiferencia.NO_PUBLICADO, p, null)));
        return diferencias;
    }

    /**
     * La liga publica 00:00 mientras no confirma el horario: eso no es un cambio.
     */
    private static Optional<DiferenciaFixture> revisarHorario(PartidoPublicado publicado, List<Partido> mismoPartido) {
        boolean procesado = mismoPartido.stream().anyMatch(p -> p.getEstado() == EstadoPartido.PROCESADO);
        Partido partido = mismoPartido.get(0);

        if (procesado
                || publicado.fechaHora().toLocalTime().equals(LocalTime.MIDNIGHT)
                || publicado.fechaHora().equals(partido.getFechaHora())) {
            return Optional.empty();
        }
        return Optional.of(diferencia(TipoDiferencia.HORARIO, partido, publicado.fechaHora()));
    }

    private static DiferenciaFixture diferencia(TipoDiferencia tipo, Partido partido, LocalDateTime fechaLiga) {
        return new DiferenciaFixture(tipo, partido.getEquipoLocal().getNombre(),
                partido.getEquipoVisitante().getNombre(), partido.getJornada().getNumero(),
                partido.getFechaHora(), fechaLiga, partido.getId());
    }

    private static boolean esLegible(PartidoPublicado p) {
        return p.local() != null && p.visitante() != null && p.fechaHora() != null;
    }

    private static boolean mismosEquipos(Partido partido, PartidoPublicado publicado) {
        return partido.getEquipoLocal().getId().equals(publicado.local().getId())
                && partido.getEquipoVisitante().getId().equals(publicado.visitante().getId());
    }

    private static String claveDe(Partido p) {
        return clave(p.getEquipoLocal().getId(), p.getEquipoVisitante().getId(), p.getFechaHora().toLocalDate());
    }

    private static String claveDe(PartidoPublicado p) {
        return clave(p.local().getId(), p.visitante().getId(), p.fechaHora().toLocalDate());
    }

    private static String clave(Long local, Long visitante, LocalDate dia) {
        return local + "|" + visitante + "|" + dia;
    }
}
