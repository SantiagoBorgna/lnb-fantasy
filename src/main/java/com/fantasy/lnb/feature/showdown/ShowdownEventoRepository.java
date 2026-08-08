package com.fantasy.lnb.feature.showdown;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShowdownEventoRepository extends JpaRepository<ShowdownEvento, Long> {
    Optional<ShowdownEvento> findByCodigoInscripcion(String codigoInscripcion);
}
