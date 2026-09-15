package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.mercado.MercadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreciosCronJob {

    private final MercadoService mercadoService;

    /**
     * Ya no corre en un horario fijo — la dispara JornadaTransicionCronJob una
     * única vez, justo cuando una jornada pasa a FINALIZADA. Así el precio se
     * recalcula exactamente una vez por jornada, nunca con la jornada todavía
     * EN_JUEGO ni repetido varias veces sobre los mismos datos.
     *
     * También queda disponible para disparo manual desde el panel de Admin →
     * Utilidades.
     */
    public void actualizarPrecios() {
        log.info("[PRECIOS-CRON] Iniciando actualización de precios de mercado...");
        mercadoService.actualizarPreciosTodos();
        log.info("[PRECIOS-CRON] Ciclo de precios finalizado.");
    }
}