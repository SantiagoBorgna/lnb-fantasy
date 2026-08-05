package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorTecnicoService {

    private final DirectorTecnicoRepository dtRepo;
    private final PartidoRepository partidoRepo;
    private final MotorPuntuacionPlantel motorPuntuacionPlantel;

    @Transactional
    public void actualizarPromediosDts() {
        List<DirectorTecnico> dts = dtRepo.findAll();
        int actualizados = 0;

        for (DirectorTecnico dt : dts) {
            Long equipoId = dt.getEquipoReal().getId();
            
            // Buscar partidos finalizados de su equipo
            List<Partido> partidos = partidoRepo.findByEstado(EstadoPartido.FINALIZADO).stream()
                .filter(p -> p.getEquipoLocal().getId().equals(equipoId) || p.getEquipoVisitante().getId().equals(equipoId))
                .toList();

            if (partidos.isEmpty()) {
                dt.setPromedioFantasy(0.0);
            } else {
                double sumaPuntajes = 0.0;
                for (Partido p : partidos) {
                    boolean esLocal = p.getEquipoLocal().getId().equals(equipoId);
                    int puntosDT = esLocal ? p.getPuntosLocal() : p.getPuntosVisitante();
                    int puntosRival = esLocal ? p.getPuntosVisitante() : p.getPuntosLocal();
                    
                    double puntajeDt = motorPuntuacionPlantel.calcularPuntajeDt(puntosDT, puntosRival);
                    sumaPuntajes += puntajeDt;
                }
                
                double promedio = sumaPuntajes / partidos.size();
                dt.setPromedioFantasy(Math.round(promedio * 100.0) / 100.0);
            }
            actualizados++;
        }
        
        log.info("[DT-PROMEDIOS] Actualización completada. DTs actualizados: {}", actualizados);
    }
}
