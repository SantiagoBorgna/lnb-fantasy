package com.fantasy.lnb.feature.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JugadorRealRepository extends JpaRepository<JugadorReal, Long> {
    Optional<JugadorReal> findByGesId(Long gesId);
}