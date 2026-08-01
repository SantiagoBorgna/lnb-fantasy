package com.fantasy.lnb.feature.torneo;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Component("torneoCacheHelper")
@RequiredArgsConstructor
public class TorneoCacheHelper {

    private final TorneoRepository torneoRepo;

    @Transactional(readOnly = true)
    public boolean draftFinalizado(Long torneoId) {
        if (torneoId == null) return false;
        return torneoRepo.findById(torneoId)
                .map(t -> t.getEstadoDraft() == EstadoDraft.FINALIZADO)
                .orElse(false);
    }
}
