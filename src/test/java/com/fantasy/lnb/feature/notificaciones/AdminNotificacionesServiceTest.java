package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.notificaciones.dto.EnviarNotificacionRequest;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificacionesServiceTest {

    @Mock
    private PushNotificationService pushService;
    @Mock
    private SuscripcionPushRepository suscripcionRepo;

    @InjectMocks
    private AdminNotificacionesService service;

    private final Usuario admin = Usuario.builder().id(1L).email("admin@test.com").build();

    @BeforeEach
    void ejecutarEnElMismoHilo() {
        service.setExecutor(Runnable::run);
    }

    @Test
    void laPruebaSoloLeLlegaAlAdmin() {
        when(suscripcionRepo.findByUsuario_Id(1L)).thenReturn(List.of(new SuscripcionPush(), new SuscripcionPush()));

        long dispositivos = service.enviar(new EnviarNotificacionRequest(" Hola ", "Novedades", "/canchita", true), admin);

        assertEquals(2, dispositivos);
        verify(pushService).enviarNotificacionAUsuario(admin, "Hola", "Novedades", "/canchita", "ANUNCIO");
        verify(pushService, never()).enviarNotificacionMasiva(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void laPruebaSinDispositivosDelAdminAvisa() {
        when(suscripcionRepo.findByUsuario_Id(1L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.enviar(new EnviarNotificacionRequest("Hola", "Novedades", "/", true), admin));
        verifyNoInteractions(pushService);
    }

    @Test
    void elEnvioATodosUsaLaUrlElegida() {
        when(suscripcionRepo.count()).thenReturn(180L);

        long dispositivos = service.enviar(new EnviarNotificacionRequest("Hola", "Novedades", "/torneos", false), admin);

        assertEquals(180, dispositivos);
        verify(pushService).enviarNotificacionMasiva("Hola", "Novedades", "/torneos", "ANUNCIO");
    }

    @Test
    void sinDestinoVaAlInicio() {
        when(suscripcionRepo.count()).thenReturn(5L);

        service.enviar(new EnviarNotificacionRequest("Hola", "Novedades", null, false), admin);

        verify(pushService).enviarNotificacionMasiva("Hola", "Novedades", "/", "ANUNCIO");
    }

    @Test
    void rechazaDestinosFueraDeLaApp() {
        for (String url : List.of("https://otro.com", "//otro.com", "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.enviar(new EnviarNotificacionRequest("Hola", "Novedades", url, false), admin), url);
        }
        verifyNoInteractions(pushService);
    }

    @Test
    void validaTituloYMensaje() {
        assertThrows(IllegalArgumentException.class,
                () -> service.enviar(new EnviarNotificacionRequest("  ", "Novedades", "/", false), admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.enviar(new EnviarNotificacionRequest("x".repeat(61), "Novedades", "/", false), admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.enviar(new EnviarNotificacionRequest("Hola", "x".repeat(201), "/", false), admin));
        verify(pushService, never()).enviarNotificacionMasiva(any(), any(), any(), any());
    }
}
