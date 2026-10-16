package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJornadaRequest;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.jornada.dto.JornadaDto;
import com.fantasy.lnb.feature.mercado.PropuestaTraspasoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * La lista de jornadas está cacheada: una jornada creada, editada o borrada desde
 * el admin tiene que verse enseguida (antes quedaba oculta hasta que vencía el caché).
 */
@SpringJUnitConfig(AdminJornadasCacheTest.Config.class)
class AdminJornadasCacheTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        JornadaRepository jornadaRepo() {
            return Mockito.mock(JornadaRepository.class);
        }

        @Bean
        JornadaService jornadaService(JornadaRepository jornadaRepo) {
            return new JornadaService(jornadaRepo, Mockito.mock(EstadisticaPartidoRepository.class),
                    Mockito.mock(PartidoRepository.class), Mockito.mock(PropuestaTraspasoRepository.class));
        }

        @Bean
        AdminJornadasService adminJornadasService(JornadaRepository jornadaRepo) {
            return new AdminJornadasService(jornadaRepo);
        }
    }

    @Autowired JornadaRepository jornadaRepo;
    @Autowired JornadaService jornadaService;
    @Autowired AdminJornadasService adminJornadasService;
    @Autowired CacheManager cacheManager;

    private final List<Jornada> enBase = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        enBase.clear();
        enBase.add(jornada(1L, 1));
        when(jornadaRepo.findAll()).thenAnswer(inv -> List.copyOf(enBase));
        when(jornadaRepo.save(any(Jornada.class))).thenAnswer(inv -> {
            Jornada j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId((long) enBase.size() + 1);
                enBase.add(j);
            }
            return j;
        });
        when(jornadaRepo.findById(anyLong())).thenAnswer(inv -> enBase.stream()
                .filter(j -> j.getId().equals(inv.getArgument(0))).findFirst());
        when(jornadaRepo.existsById(anyLong())).thenReturn(true);
    }

    @Test
    void unaJornadaCreadaAparaceEnLaListaEnseguida() {
        assertEquals(List.of(1), numeros(jornadaService.listarTodas())); // queda en caché

        adminJornadasService.crearJornada(request(2));

        assertEquals(List.of(1, 2), numeros(jornadaService.listarTodas()));
    }

    @Test
    void editarUnaJornadaSeVeEnseguida() {
        jornadaService.listarTodas();

        adminJornadasService.actualizarJornada(1L, request(5));

        assertEquals(List.of(5), numeros(jornadaService.listarTodas()));
    }

    @Test
    void borrarUnaJornadaSeVeEnseguida() {
        jornadaService.listarTodas();
        Mockito.doAnswer(inv -> enBase.removeIf(j -> j.getId().equals(inv.getArgument(0))))
                .when(jornadaRepo).deleteById(anyLong());

        adminJornadasService.eliminarJornada(1L);

        assertEquals(List.of(), numeros(jornadaService.listarTodas()));
    }

    private static List<Integer> numeros(List<JornadaDto> jornadas) {
        return jornadas.stream().map(JornadaDto::getNumero).toList();
    }

    private static Jornada jornada(Long id, int numero) {
        return Jornada.builder().id(id).numero(numero).estado(EstadoJornada.ABIERTA_A_CAMBIOS)
                .fechaInicio(LocalDateTime.of(2026, 10, 16, 19, 0))
                .fechaFin(LocalDateTime.of(2026, 10, 19, 0, 0)).build();
    }

    private static AdminJornadaRequest request(int numero) {
        AdminJornadaRequest r = new AdminJornadaRequest();
        r.setNumero(numero);
        r.setEstado(EstadoJornada.ABIERTA_A_CAMBIOS);
        r.setFechaInicio(LocalDateTime.of(2026, 10, 16, 19, 0));
        r.setFechaFin(LocalDateTime.of(2026, 10, 19, 0, 0));
        return r;
    }
}
