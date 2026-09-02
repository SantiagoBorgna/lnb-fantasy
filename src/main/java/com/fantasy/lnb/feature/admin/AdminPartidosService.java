package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminPartidoRequest;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.scraper.ScraperCronJob;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public List<Partido> listarPartidos(Long jornadaId) {
        if (jornadaId != null) {
            return partidoRepo.findByJornada_Id(jornadaId);
        }
        return partidoRepo.findAll();
    }

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
