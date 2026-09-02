package com.fantasy.lnb.config;

import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestEquipos implements CommandLineRunner {
    private final EquipoRealRepository equipoRepo;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("CANTIDAD DE EQUIPOS: " + equipoRepo.count());
    }
}
