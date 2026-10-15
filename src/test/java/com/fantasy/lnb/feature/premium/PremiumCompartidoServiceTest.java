package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PremiumCompartidoServiceTest {

    private UsuarioRepository repo;
    private final AtomicLong ahora = new AtomicLong(1_000_000);
    private final AtomicInteger consultas = new AtomicInteger();
    private final AtomicReference<Optional<Boolean>> respuesta = new AtomicReference<>(Optional.of(true));
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        usuario = Usuario.builder().id(1L).email("a@b.com").isPremium(false).build();
    }

    private PremiumCompartidoService servicio(String fuenteUrl) {
        return new PremiumCompartidoService(repo, fuenteUrl, token -> {
            consultas.incrementAndGet();
            assertEquals("TOKEN", token);
            return respuesta.get();
        }, ahora::get);
    }

    @Test
    void sinFuenteConfiguradaNoConsultaNada() {
        PremiumCompartidoService s = servicio("");

        s.sincronizar(usuario, "TOKEN");

        assertFalse(s.esCompartido());
        assertEquals(0, consultas.get());
        assertFalse(usuario.isPremium());
    }

    @Test
    void copiaElPremiumDeLaLigaNacional() {
        PremiumCompartidoService s = servicio("https://lnb.example.com");

        s.sincronizar(usuario, "TOKEN");

        assertTrue(s.esCompartido());
        assertTrue(usuario.isPremium());
        verify(repo).save(usuario);
    }

    @Test
    void siVencioEnLaLigaNacionalTambienSeLeSacaAca() {
        usuario.setPremium(true);
        respuesta.set(Optional.of(false));

        servicio("https://lnb.example.com").sincronizar(usuario, "TOKEN");

        assertFalse(usuario.isPremium());
        verify(repo).save(usuario);
    }

    @Test
    void siNoCambioNoGuarda() {
        usuario.setPremium(true);

        servicio("https://lnb.example.com").sincronizar(usuario, "TOKEN");

        verify(repo, never()).save(any());
    }

    @Test
    void siLaLigaNacionalNoRespondeDejaLoQueHabia() {
        usuario.setPremium(true);
        respuesta.set(Optional.empty());

        servicio("https://lnb.example.com").sincronizar(usuario, "TOKEN");

        assertTrue(usuario.isPremium());
        verify(repo, never()).save(any());
    }

    @Test
    void consultaComoMuchoUnaVezCadaCincoMinutosPorUsuario() {
        PremiumCompartidoService s = servicio("https://lnb.example.com");

        s.sincronizar(usuario, "TOKEN");
        ahora.addAndGet(PremiumCompartidoService.CADA_MS - 1);
        s.sincronizar(usuario, "TOKEN");
        assertEquals(1, consultas.get());

        ahora.addAndGet(1);
        s.sincronizar(usuario, "TOKEN");
        assertEquals(2, consultas.get());
    }
}
