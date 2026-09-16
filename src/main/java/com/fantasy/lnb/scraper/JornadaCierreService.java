package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;
import com.fantasy.lnb.feature.plantel.PuntuacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean separado (no un método más de JornadaTransicionCronJob) para que el
 * @Transactional de cerrarJornadaCompleta se aplique de verdad: al ser una
 * llamada externa a otro bean, pasa por el proxy de Spring. Ponerlo como
 * método propio del cron job y llamarlo con this. no abre transacción, y
 * auto-inyectarse a sí mismo como dependencia rompe el arranque (ciclo de
 * beans) salvo que se configure lombok.config con copyableAnnotations para
 * @Lazy, que este proyecto no tiene.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JornadaCierreService {

        private final JornadaService jornadaService;
        private final PuntuacionService puntuacionService;
        private final PlantelClonadoService plantelClonadoService;
        private final com.fantasy.lnb.feature.dt.DirectorTecnicoService directorTecnicoService;
        private final PreciosCronJob preciosCronJob;

        /**
         * Cierre "real" de una jornada: cambia su estado, calcula puntajes,
         * actualiza promedios de DTs, recalcula precios y clona planteles hacia
         * la próxima jornada — todo en una única transacción. Si cualquier paso
         * falla, se revierte todo (la jornada vuelve a quedar EN_JUEGO) para que
         * el próximo ciclo del cron reintente el cierre completo desde cero.
         */
        @Transactional
        public void cerrarJornadaCompleta(Jornada jornada) {
                jornadaService.finalizarJornada(jornada.getId());
                puntuacionService.calcularPuntajesDeJornada(jornada.getId(), true);
                directorTecnicoService.actualizarPromediosDts();
                preciosCronJob.actualizarPrecios();

                int clonados = plantelClonadoService.clonarDesdeJornadaFinalizada(jornada);
                if (clonados > 0) {
                        log.info("[TRANSICION] Clonado masivo completado. J{} fue base para {} planteles nuevos.",
                                        jornada.getNumero(), clonados);
                }
        }
}
