package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.mercado.MercadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreciosCronJob {

    private final MercadoService mercadoService;

    /**
     * Corre todos los días a las 4 AM.
     * El scraper corre a las 2 AM → a las 4 AM ya están los datos persistidos.
     * En producción estos horarios se externalizan a variables de entorno.
     *
     * Para probar manualmente sin esperar, cambiá temporalmente a:
     * 
     * @Scheduled(cron = "0 * * * * *") ← cada minuto
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void actualizarPrecios() {
        log.info("[PRECIOS-CRON] Iniciando actualización de precios de mercado...");
        mercadoService.actualizarPreciosTodos();
        log.info("[PRECIOS-CRON] Ciclo de precios finalizado.");
    }
}