package com.fantasy.lnb.feature.jornada;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Component("jornadaCacheHelper")
@RequiredArgsConstructor
public class JornadaCacheHelper {

    private final JornadaRepository jornadaRepo;

    @Transactional(readOnly = true)
    public boolean estaFinalizada(Long jornadaId) {
        if (jornadaId == null) return false;
        return jornadaRepo.findById(jornadaId)
                .map(j -> j.getEstado() == EstadoJornada.FINALIZADA)
                .orElse(false);
    }
}
