package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifica con el caché real de Spring que el límite pedido forma parte de la clave:
 * antes, el primer pedido de una jornada fijaba el tamaño de la lista para todos.
 */
@SpringJUnitConfig(RankingServiceCacheTest.Config.class)
class RankingServiceCacheTest {

    private static final Long JORNADA_ID = 7L;

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        PlantelJornadaRepository plantelJornadaRepo() {
            return Mockito.mock(PlantelJornadaRepository.class);
        }

        @Bean
        EquipoVirtualRepository equipoVirtualRepo() {
            return Mockito.mock(EquipoVirtualRepository.class);
        }

        @Bean
        RankingService rankingService(EquipoVirtualRepository equipoVirtualRepo,
                                      PlantelJornadaRepository plantelJornadaRepo) {
            return new RankingService(equipoVirtualRepo, plantelJornadaRepo,
                    Mockito.mock(TorneoRepository.class), Mockito.mock(TorneoEquipoRepository.class));
        }
    }

    @Autowired
    private RankingService service;
    @Autowired
    private PlantelJornadaRepository plantelJornadaRepo;
    @Autowired
    private EquipoVirtualRepository equipoVirtualRepo;

    @Test
    void elRankingDeJornadaRespetaElLimiteAunqueYaEsteCacheado() {
        List<PlantelJornada> planteles = LongStream.rangeClosed(1, 10)
                .mapToObj(i -> PlantelJornada.builder()
                        .usuario(Usuario.builder().id(i).nombreDisplay("U" + i).build())
                        .puntajeObtenidoFecha((double) i)
                        .build())
                .toList();
        when(plantelJornadaRepo.findByJornada_IdAndTorneoIsNull(JORNADA_ID)).thenReturn(planteles);
        when(equipoVirtualRepo.findByUsuario_IdIn(any())).thenReturn(List.of());

        assertEquals(5, service.obtenerRankingJornada(JORNADA_ID, 5).size());
        assertEquals(10, service.obtenerRankingJornada(JORNADA_ID, 100).size());
        // Y el top 5 sigue saliendo del caché con su tamaño
        assertEquals(5, service.obtenerRankingJornada(JORNADA_ID, 5).size());
    }
}
