package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJornadaRequest;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminJornadasService {

    private final JornadaRepository jornadaRepository;

    public List<Jornada> listarJornadas() {
        return jornadaRepository.findAll();
    }

    @Transactional
    public Jornada crearJornada(AdminJornadaRequest request) {
        Jornada jornada = Jornada.builder()
                .numero(request.getNumero())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .estado(request.getEstado())
                .build();
        return jornadaRepository.save(jornada);
    }

    @Transactional
    public Jornada actualizarJornada(Long id, AdminJornadaRequest request) {
        Jornada jornada = jornadaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada"));
        jornada.setNumero(request.getNumero());
        jornada.setFechaInicio(request.getFechaInicio());
        jornada.setFechaFin(request.getFechaFin());
        jornada.setEstado(request.getEstado());
        return jornadaRepository.save(jornada);
    }

    @Transactional
    public void eliminarJornada(Long id) {
        if (!jornadaRepository.existsById(id)) {
            throw new IllegalArgumentException("Jornada no encontrada");
        }
        jornadaRepository.deleteById(id);
    }
}
