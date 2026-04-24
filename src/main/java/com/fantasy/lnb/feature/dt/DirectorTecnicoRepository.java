package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DirectorTecnicoRepository extends JpaRepository<DirectorTecnico, Long> {

    // Para el mercado de DTs — filtrado por equipo
    List<DirectorTecnico> findByEquipoReal_Id(Long equipoId);

    // Solo DTs disponibles (no lesionados ni suspendidos)
    List<DirectorTecnico> findByEstado(EstadoJugador estado);

    // Búsqueda por nombre para el buscador del mercado
    List<DirectorTecnico> findByNombreCompletoContainingIgnoreCase(String nombre);
}