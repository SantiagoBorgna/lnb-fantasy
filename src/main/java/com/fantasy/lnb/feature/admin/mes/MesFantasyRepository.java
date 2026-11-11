package com.fantasy.lnb.feature.admin.mes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MesFantasyRepository extends JpaRepository<MesFantasy, Long> {
    List<MesFantasy> findAllByOrderByCreadoEnDesc();
}
