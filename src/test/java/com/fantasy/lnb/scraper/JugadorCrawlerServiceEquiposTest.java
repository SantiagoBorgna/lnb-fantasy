package com.fantasy.lnb.scraper;

import com.fantasy.lnb.config.LigaConfig;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Al arrancar la temporada muchos equipos todavía no tienen jugadores en GES:
 * igual tienen que crearse, y en orden alfabético para que los ids sigan ese orden.
 */
class JugadorCrawlerServiceEquiposTest {

    private final List<EquipoReal> enBase = new ArrayList<>();
    private JugadorCrawlerService crawler;

    @BeforeEach
    void setUp() {
        EquipoRealRepository equipoRepo = mock(EquipoRealRepository.class);
        when(equipoRepo.findByNombreIgnoreCase(anyString())).thenAnswer(inv -> enBase.stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(inv.getArgument(0)))
                .findFirst());
        when(equipoRepo.save(any(EquipoReal.class))).thenAnswer(inv -> {
            EquipoReal e = inv.getArgument(0);
            e.setId((long) enBase.size() + 1);
            enBase.add(e);
            return e;
        });
        crawler = new JugadorCrawlerService(mock(JugadorRealRepository.class), equipoRepo,
                new LigaConfig("liga-argentina", "Liga Argentina", "laligaargentina", "https://www.laliganacional.com.ar"));
    }

    @Test
    void creaTodosLosEquiposEnOrdenAlfabetico() {
        int creados = crawler.crearEquiposFaltantes(List.of("RIVER", "AMANCAY LR", "Ñuñorco", "ÁGUILAS", "QUILMES MDP"));

        assertEquals(5, creados);
        assertEquals(List.of("ÁGUILAS", "AMANCAY LR", "Ñuñorco", "QUILMES MDP", "RIVER"),
                enBase.stream().map(EquipoReal::getNombre).toList());
        assertEquals(1L, enBase.get(0).getId());
        assertEquals("#000000", enBase.get(0).getColorPrincipal());
        assertEquals("AMA", enBase.get(1).getSigla());
    }

    @Test
    void noDuplicaLosQueYaExisten() {
        enBase.add(EquipoReal.builder().id(1L).nombre("River").build());

        int creados = crawler.crearEquiposFaltantes(List.of("RIVER", "QUILMES MDP"));

        assertEquals(1, creados);
        assertEquals(List.of("River", "QUILMES MDP"), enBase.stream().map(EquipoReal::getNombre).toList());
    }

    @Test
    void ignoraNombresVacios() {
        assertEquals(0, crawler.crearEquiposFaltantes(List.of("", "  ")));
        assertEquals(Optional.empty(), enBase.stream().findFirst());
    }
}
