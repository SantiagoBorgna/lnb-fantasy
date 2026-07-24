package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropuestaTraspasoRepository extends JpaRepository<PropuestaTraspaso, Long> {
    
    org.springframework.data.domain.Page<PropuestaTraspaso> findByTorneo_IdAndEquipoProponente_Usuario_IdOrTorneo_IdAndEquipoReceptor_Usuario_IdOrderByFechaCreacionDesc(
            Long torneoId1, Long usuarioId1, Long torneoId2, Long usuarioId2, org.springframework.data.domain.Pageable pageable);

    List<PropuestaTraspaso> findByEstado(EstadoPropuestaTraspaso estado);

    @Query("SELECT p FROM PropuestaTraspaso p JOIN p.jugadoresOfrecidos jo WHERE jo.id = :jugadorId AND p.estado = 'PENDIENTE'")
    List<PropuestaTraspaso> findPendientesByJugadorOfrecido(Long jugadorId);

    @Query("SELECT p FROM PropuestaTraspaso p JOIN p.jugadoresSolicitados js WHERE js.id = :jugadorId AND p.estado = 'PENDIENTE'")
    List<PropuestaTraspaso> findPendientesByJugadorSolicitado(Long jugadorId);

    @Query("SELECT p FROM PropuestaTraspaso p WHERE (p.dtOfrecido.id = :dtId OR p.dtSolicitado.id = :dtId) AND p.estado = 'PENDIENTE'")
    List<PropuestaTraspaso> findPendientesByDt(Long dtId);

    @Query("SELECT p FROM PropuestaTraspaso p WHERE p.equipoProponente.usuario.id = :usuarioId AND p.torneo.id = :torneoId AND p.estado = 'PENDIENTE'")
    List<PropuestaTraspaso> findPendientesSalientes(Long usuarioId, Long torneoId);
}
