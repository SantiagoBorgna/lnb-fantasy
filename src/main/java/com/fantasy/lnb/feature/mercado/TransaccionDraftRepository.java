package com.fantasy.lnb.feature.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TransaccionDraftRepository extends JpaRepository<TransaccionDraft, Long> {
    List<TransaccionDraft> findByTorneo_IdAndJornada_IdOrderByFechaDesc(Long torneoId, Long jornadaId);
    
    Page<TransaccionDraft> findByTorneo_IdOrderByFechaDesc(Long torneoId, Pageable pageable);
}
