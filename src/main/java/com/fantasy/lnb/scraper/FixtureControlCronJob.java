package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.notificaciones.AvisoAdminService;
import com.fantasy.lnb.scraper.FixtureComparador.DiferenciaFixture;
import com.fantasy.lnb.scraper.FixtureControlService.ResultadoControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controla el fixture de la liga cada 6 horas y les avisa a los admins lo nuevo. Cada
 * diferencia se avisa una vez. Si la misma vuelve a aparecer después de resolverse,
 * se avisa de nuevo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixtureControlCronJob {

    static final String CLAVE_ERROR_LECTURA = "ERROR_LECTURA";

    // Un corte puntual de la web de la liga no merece un aviso: se avisa recién al
    // segundo control seguido que falla, 6 horas después del primero.
    private static final int FALLOS_PARA_AVISAR = 2;
    private static final int MAX_DIFERENCIAS_EN_AVISO = 3;

    private final FixtureControlService controlService;
    private final AvisoFixtureRepository avisoRepo;
    private final AvisoAdminService avisoAdmin;

    private int fallosSeguidos = 0;

    @Scheduled(cron = "0 15 */6 * * *")
    public void controlarYAvisar() {
        try {
            ResultadoControl resultado = controlService.controlar();

            if (resultado.exitoso()) {
                fallosSeguidos = 0;
                avisarDiferencias(resultado.diferencias());
            } else {
                registrarFallo(resultado.error());
            }
        } catch (Exception e) {
            log.error("[CONTROL-FIXTURE] Error en el control: {}", e.getMessage(), e);
            registrarFallo(e.getMessage());
        }
    }

    private void avisarDiferencias(List<DiferenciaFixture> diferencias) {
        Map<String, AvisoFixture> avisados = avisoRepo.findAll().stream()
                .collect(Collectors.toMap(AvisoFixture::getClave, Function.identity()));

        List<DiferenciaFixture> nuevas = diferencias.stream()
                .filter(d -> !avisados.containsKey(d.clave()))
                .toList();

        if (!nuevas.isEmpty()) {
            avisoAdmin.avisar("cambios en el fixture", armarMensaje(nuevas));
            nuevas.forEach(d -> guardarAviso(d.clave()));
        }

        // Lo que ya no es una diferencia se olvida, incluido el aviso de error de lectura
        Set<String> vigentes = diferencias.stream().map(DiferenciaFixture::clave).collect(Collectors.toSet());
        avisados.values().stream()
                .filter(a -> !vigentes.contains(a.getClave()))
                .forEach(avisoRepo::delete);

        log.info("[CONTROL-FIXTURE] {} diferencia(s), {} nueva(s).", diferencias.size(), nuevas.size());
    }

    private void registrarFallo(String error) {
        fallosSeguidos++;

        if (fallosSeguidos < FALLOS_PARA_AVISAR) {
            return;
        }

        boolean yaAvisado = avisoRepo.findAll().stream()
                .anyMatch(a -> CLAVE_ERROR_LECTURA.equals(a.getClave()));
        if (!yaAvisado) {
            avisoAdmin.avisar("no se pudo controlar el fixture", String.valueOf(error));
            guardarAviso(CLAVE_ERROR_LECTURA);
        }
    }

    private void guardarAviso(String clave) {
        avisoRepo.save(AvisoFixture.builder().clave(clave).avisadoEn(LocalDateTime.now()).build());
    }

    private String armarMensaje(List<DiferenciaFixture> nuevas) {
        String detalle = nuevas.stream()
                .limit(MAX_DIFERENCIAS_EN_AVISO)
                .map(DiferenciaFixture::descripcion)
                .collect(Collectors.joining(" · "));

        int restantes = nuevas.size() - MAX_DIFERENCIAS_EN_AVISO;
        return restantes > 0 ? detalle + " · y " + restantes + " más" : detalle;
    }
}
