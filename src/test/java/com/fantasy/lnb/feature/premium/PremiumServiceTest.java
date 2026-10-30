package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.plantel.PlantelService;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PremiumServiceTest {

    private UsuarioRepository repo;
    private PremiumService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        servicio = new PremiumService(repo, mock(PlantelService.class));
        usuario = Usuario.builder().id(1L).email("a@b.com").isPremium(false).build();
        when(repo.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void primerPagoDaUnMesDesdeLaFechaDelPago() {
        LocalDateTime pago = LocalDateTime.now().minusHours(1);

        servicio.acreditarPago(1L, pago, "pago 1");

        assertTrue(usuario.isPremium());
        assertEquals(pago.plusMonths(1), usuario.getPremiumHasta());
        verify(repo).save(usuario);
    }

    @Test
    void avisoRepetidoDelMismoPagoNoSumaOtroMes() {
        LocalDateTime pago = LocalDateTime.now().minusDays(20);
        servicio.acreditarPago(1L, pago, "pago 1");

        // MP reenvía el aviso del mismo pago 20 días después (el usuario ya canceló la suscripción)
        servicio.acreditarPago(1L, pago, "pago 1");

        assertEquals(pago.plusMonths(1), usuario.getPremiumHasta());
        verify(repo, times(1)).save(any());
    }

    @Test
    void avisoDeLaSuscripcionDespuesDelPagoNoExtiende() {
        LocalDateTime creada = LocalDateTime.now().minusDays(10);
        LocalDateTime pago = creada.plusMinutes(2);
        servicio.acreditarPago(1L, pago, "pago 1");

        servicio.acreditarPago(1L, creada, "suscripción X");

        assertEquals(pago.plusMonths(1), usuario.getPremiumHasta());
    }

    @Test
    void pagoDeUnMesQueYaTerminoNoDaPremium() {
        servicio.acreditarPago(1L, LocalDateTime.now().minusMonths(2), "pago viejo");

        assertFalse(usuario.isPremium());
        assertNull(usuario.getPremiumHasta());
        verify(repo, never()).save(any());
    }

    @Test
    void cobroDelMesSiguienteExtiendeDesdeEseCobro() {
        LocalDateTime primero = LocalDateTime.now().minusMonths(1).plusDays(1);
        servicio.acreditarPago(1L, primero, "pago 1");

        LocalDateTime segundo = primero.plusMonths(1);
        servicio.acreditarPago(1L, segundo, "pago 2");

        assertEquals(segundo.plusMonths(1), usuario.getPremiumHasta());
    }

    @Test
    void nuncaAcortaUnPremiumQueLlegaMasLejos() {
        LocalDateTime lejos = LocalDateTime.now().plusMonths(3);
        usuario.setPremium(true);
        usuario.setPremiumHasta(lejos);

        servicio.acreditarPago(1L, LocalDateTime.now(), "pago 1");

        assertEquals(lejos, usuario.getPremiumHasta());
    }
}
