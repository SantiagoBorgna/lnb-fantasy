package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.notificaciones.AvisoAdminService;
import com.fantasy.lnb.scraper.FixtureComparador.DiferenciaFixture;
import com.fantasy.lnb.scraper.FixtureComparador.TipoDiferencia;
import com.fantasy.lnb.scraper.FixtureControlService.ResultadoControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixtureControlCronJobTest {

    private static final LocalDateTime DIA_6 = LocalDateTime.of(2026, 11, 6, 21, 35);

    private final DiferenciaFixture bocaNuevo = nuevo("BOCA");

    // "Base de datos" en memoria: los avisos tienen que sobrevivir entre corridas
    private final List<AvisoFixture> enBase = new ArrayList<>();

    private FixtureControlService controlService;
    private AvisoAdminService avisoAdmin;
    private FixtureControlCronJob cronJob;

    @BeforeEach
    void setUp() {
        controlService = mock(FixtureControlService.class);
        avisoAdmin = mock(AvisoAdminService.class);
        AvisoFixtureRepository avisoRepo = mock(AvisoFixtureRepository.class);

        when(avisoRepo.findAll()).thenAnswer(inv -> new ArrayList<>(enBase));
        when(avisoRepo.save(any())).thenAnswer(inv -> {
            enBase.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        doAnswer(inv -> {
            enBase.remove((AvisoFixture) inv.getArgument(0));
            return null;
        }).when(avisoRepo).delete(any());

        cronJob = new FixtureControlCronJob(controlService, avisoRepo, avisoAdmin);
    }

    @Test
    void unaDiferenciaSeAvisaUnaSolaVez() {
        when(controlService.controlar()).thenReturn(exito(bocaNuevo));

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        verify(avisoAdmin, times(1)).avisar(eq("cambios en el fixture"), contains("BOCA vs SAN MARTIN"));
    }

    @Test
    void sinDiferenciasNoAvisa() {
        when(controlService.controlar()).thenReturn(exito());

        cronJob.controlarYAvisar();

        verify(avisoAdmin, never()).avisar(anyString(), anyString());
    }

    @Test
    void unaDiferenciaResueltaQueVuelveAAparecerSeAvisaDeNuevo() {
        when(controlService.controlar()).thenReturn(exito(bocaNuevo), exito(), exito(bocaNuevo));

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();
        assertTrue(enBase.isEmpty(), "al resolverse se olvida");
        cronJob.controlarYAvisar();

        verify(avisoAdmin, times(2)).avisar(anyString(), anyString());
    }

    @Test
    void soloAvisaLasDiferenciasNuevas() {
        DiferenciaFixture quimsaNuevo = nuevo("QUIMSA");
        when(controlService.controlar()).thenReturn(exito(bocaNuevo), exito(bocaNuevo, quimsaNuevo));

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        verify(avisoAdmin).avisar(anyString(), eq(quimsaNuevo.descripcion()));
    }

    @Test
    void elMensajeResumeLasDiferenciasQueNoEntran() {
        when(controlService.controlar()).thenReturn(
                exito(nuevo("A"), nuevo("B"), nuevo("C"), nuevo("D"), nuevo("E")));

        cronJob.controlarYAvisar();

        verify(avisoAdmin).avisar(anyString(), contains("y 2 más"));
    }

    @Test
    void unFalloPuntualNoAvisa() {
        when(controlService.controlar()).thenReturn(fallo(), exito());

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        verify(avisoAdmin, never()).avisar(anyString(), anyString());
    }

    @Test
    void dosFallosSeguidosAvisanUnaSolaVezAunqueSigaFallando() {
        when(controlService.controlar()).thenReturn(fallo());

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        verify(avisoAdmin, times(1)).avisar(eq("no se pudo controlar el fixture"), eq("sitio caído"));
    }

    @Test
    void unaExcepcionDelControlCuentaComoFallo() {
        when(controlService.controlar()).thenThrow(new RuntimeException("base caída"));

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        verify(avisoAdmin).avisar(eq("no se pudo controlar el fixture"), eq("base caída"));
    }

    @Test
    void elErrorSeVuelveAAvisarSiFallaDeNuevoDespuesDeRecuperarse() {
        when(controlService.controlar()).thenReturn(fallo(), fallo(), exito(), fallo(), fallo());

        for (int i = 0; i < 5; i++) {
            cronJob.controlarYAvisar();
        }

        verify(avisoAdmin, times(2)).avisar(eq("no se pudo controlar el fixture"), anyString());
    }

    @Test
    void unFalloNoBorraLoYaAvisado() {
        when(controlService.controlar()).thenReturn(exito(bocaNuevo), fallo(), exito(bocaNuevo));

        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();
        cronJob.controlarYAvisar();

        assertEquals(1, enBase.size());
        verify(avisoAdmin, times(1)).avisar(anyString(), anyString());
    }

    private static DiferenciaFixture nuevo(String local) {
        return new DiferenciaFixture(TipoDiferencia.NUEVO, local, "SAN MARTIN", 5, null, DIA_6, null);
    }

    private static ResultadoControl exito(DiferenciaFixture... diferencias) {
        return new ResultadoControl(LocalDateTime.now(), null, List.of(5), null, null, List.of(diferencias));
    }

    private static ResultadoControl fallo() {
        return new ResultadoControl(LocalDateTime.now(), "sitio caído", List.of(5), null, null, List.of());
    }
}
