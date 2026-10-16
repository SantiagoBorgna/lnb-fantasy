package com.fantasy.lnb.feature.arrepentimiento;

import com.fantasy.lnb.feature.arrepentimiento.dto.SolicitudArrepentimientoRequest;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;
import com.fantasy.lnb.feature.usuario.RolUsuario;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArrepentimientoServiceTest {

    private SolicitudArrepentimientoRepository repo;
    private UsuarioRepository usuarioRepo;
    private PushNotificationService push;
    private ArrepentimientoService service;

    @BeforeEach
    void setUp() {
        repo = mock(SolicitudArrepentimientoRepository.class);
        usuarioRepo = mock(UsuarioRepository.class);
        push = mock(PushNotificationService.class);
        when(repo.save(any())).thenAnswer(inv -> {
            SolicitudArrepentimiento s = inv.getArgument(0);
            if (s.getId() == null) s.setId(123L);
            return s;
        });
        when(usuarioRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepo.findByRol(RolUsuario.ADMIN)).thenReturn(List.of());
        service = new ArrepentimientoService(repo, usuarioRepo, push);
    }

    private static SolicitudArrepentimientoRequest pedido(String email, LocalDate fecha, String motivo) {
        SolicitudArrepentimientoRequest r = new SolicitudArrepentimientoRequest();
        r.setEmail(email);
        r.setFechaContratacion(fecha);
        r.setMotivo(motivo);
        return r;
    }

    @Test
    void registraElPedidoYDevuelveElCodigo() {
        SolicitudArrepentimiento s = service.registrar(pedido("  Ana@Gmail.com ", LocalDate.now().minusDays(2), "  No lo uso  "));

        assertEquals("ARR-000123", s.getCodigo());
        assertEquals("ana@gmail.com", s.getEmail());
        assertEquals("No lo uso", s.getMotivo());
        assertNull(s.getUsuarioId());
    }

    @Test
    void vinculaElUsuarioSiElEmailEstaRegistrado() {
        when(usuarioRepo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(Usuario.builder().id(7L).build()));

        assertEquals(7L, service.registrar(pedido("ana@gmail.com", null, null)).getUsuarioId());
    }

    @Test
    void avisaALosAdmins() {
        Usuario admin = Usuario.builder().id(1L).build();
        when(usuarioRepo.findByRol(RolUsuario.ADMIN)).thenReturn(List.of(admin));

        service.registrar(pedido("ana@gmail.com", null, null));

        verify(push).enviarNotificacionAUsuario(eq(admin), eq("Botón de arrepentimiento"), contains("ARR-000123"), eq("/admin"), anyString());
    }

    @Test
    void siFallaElAvisoElPedidoIgualQuedaGuardado() {
        when(usuarioRepo.findByRol(RolUsuario.ADMIN)).thenThrow(new RuntimeException("push caído"));

        assertEquals("ARR-000123", service.registrar(pedido("ana@gmail.com", null, null)).getCodigo());
        verify(repo).save(any());
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.registrar(pedido("no-es-un-mail", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(pedido(null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(pedido("a@b.com", LocalDate.now().plusDays(1), null)));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(pedido("a@b.com", null, "x".repeat(501))));
        verify(repo, never()).save(any());
    }

    @Test
    void marcarResueltaGuardaLaFechaUnaSolaVez() {
        SolicitudArrepentimiento s = SolicitudArrepentimiento.builder().id(5L).email("a@b.com").build();
        when(repo.findById(5L)).thenReturn(Optional.of(s));

        service.marcarResuelta(5L);
        var primera = s.getResueltoEn();
        service.marcarResuelta(5L);

        assertNotNull(primera);
        assertEquals(primera, s.getResueltoEn());
        verify(repo, times(1)).save(s);
    }
}
