package com.fantasy.lnb.feature.arrepentimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudArrepentimientoRepository extends JpaRepository<SolicitudArrepentimiento, Long> {

    List<SolicitudArrepentimiento> findAllByOrderByCreadoEnDesc();
}
