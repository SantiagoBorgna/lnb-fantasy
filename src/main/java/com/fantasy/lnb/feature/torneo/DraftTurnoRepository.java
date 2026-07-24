package com.fantasy.lnb.feature.torneo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DraftTurnoRepository extends JpaRepository<DraftTurno, Long> {

    List<DraftTurno> findByTorneo_IdOrderByNumeroTurnoGlobalAsc(Long torneoId);

    // Obtiene el turno actual que se está ejecutando (el de menor número no completado)
    Optional<DraftTurno> findFirstByTorneo_IdAndCompletadoFalseOrderByNumeroTurnoGlobalAsc(Long torneoId);
    
    // Obtiene todos los turnos que ya están vencidos
    List<DraftTurno> findByCompletadoFalseAndLimiteTiempoBefore(java.time.LocalDateTime ahora);
}
