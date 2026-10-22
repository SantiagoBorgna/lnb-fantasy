package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminPartidoRequest;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.showdown.ShowdownEventoRepository;
import com.fantasy.lnb.scraper.ScraperCronJob;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPartidosService {

    private final PartidoRepository partidoRepo;
    private final JornadaRepository jornadaRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final ScraperCronJob scraperCronJob;
    private final ShowdownEventoRepository showdownRepo;

    @Transactional(readOnly = true)
    public List<Partido> listarPartidos(Long jornadaId) {
        if (jornadaId != null) {
            return partidoRepo.findByJornada_Id(jornadaId);
        }
        return partidoRepo.findAll();
    }

    @CacheEvict(value = {"jornadas", "partidos"}, allEntries = true)
    @Transactional
    public Partido actualizarPartido(Long id, AdminPartidoRequest request) {
        Partido partido = partidoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Partido no encontrado"));

        Jornada jornada = jornadaRepo.findById(request.getJornadaId())
                .orElseThrow(() -> new EntityNotFoundException("Jornada no encontrada"));

        partido.setJornada(jornada);
        partido.setFechaHora(request.getFechaHora());
        partido.setEstado(request.getEstado());
        partido.setPuntosLocal(request.getPuntosLocal());
        partido.setPuntosVisitante(request.getPuntosVisitante());

        return partidoRepo.save(partido);
    }

    /**
     * Borra un partido que no se va a jugar en su fecha (postergado sin fecha nueva).
     * Cuando se reprograme, vuelve a aparecer al sincronizar el fixture de su nueva jornada.
     * No se puede borrar si ya tiene estadísticas o un evento de Modo Estadio.
     */
    @CacheEvict(value = {"jornadas", "partidos"}, allEntries = true)
    @Transactional
    public void eliminarPartido(Long id) {
        Partido partido = partidoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Partido no encontrado"));

        if (Boolean.TRUE.equals(partido.getEstadisticasProcesadas())
                || (partido.getGesHash() != null && !estadisticaRepo.findByGesPartidoId(partido.getGesHash()).isEmpty())) {
            throw new IllegalStateException("El partido ya tiene estadísticas cargadas: no se puede eliminar.");
        }
        if (showdownRepo.existsByPartido_Id(id)) {
            throw new IllegalStateException("El partido tiene un evento de Modo Estadio: eliminalo primero.");
        }

        partidoRepo.delete(partido);
        log.info("[ADMIN] Partido eliminado: {} vs {} ({}) de la jornada {}",
                partido.getEquipoLocal().getNombre(), partido.getEquipoVisitante().getNombre(),
                partido.getFechaHora(), partido.getJornada().getNumero());
    }

    @CacheEvict(value = {"jornadas", "partidos"}, allEntries = true)
    @Transactional
    public Partido recalcularEstadisticas(Long id) {
        Partido partido = partidoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Partido no encontrado"));

        log.info("[ADMIN] Recalculando estadísticas para el partido: {} (Jornada {})", partido.getGesHash(), partido.getJornada().getNumero());

        // ELIMINAR ESTADISTICAS PREVIAS PARA QUE NO DEN CONFLICTO
        estadisticaRepo.deleteByGesPartidoId(partido.getGesHash());
        log.info("[ADMIN] Estadísticas anteriores eliminadas.");

        // Forzamos al scraper a procesar el partido como si fuera la primera vez
        partido.setEstadisticasProcesadas(false);
        partidoRepo.save(partido);
        partidoRepo.flush();

        try {
            scraperCronJob.procesarPartido(partido);
        } catch (Exception e) {
            log.error("[ADMIN] Error recalculando estadísticas para el partido {}: {}", partido.getGesHash(), e.getMessage());
            throw new RuntimeException("Error al correr el scraper: " + e.getMessage());
        }

        return partidoRepo.findById(id).orElseThrow();
    }
}
