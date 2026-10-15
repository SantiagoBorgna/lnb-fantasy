package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.usuario.RolUsuario;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AltaDesdeTokenServiceTest {

    private UsuarioRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(7L);
                u.setRol(RolUsuario.USER); // lo que hace @PrePersist
            }
            return u;
        });
    }

    @Test
    void siYaExisteDevuelveElDeLaBaseSinCrearOtro() {
        Usuario existente = Usuario.builder().id(1L).email("a@b.com").build();
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(existente));

        Optional<Usuario> r = new AltaDesdeTokenService(repo, true).buscarOCrear("a@b.com", "Ana", RolUsuario.USER);

        assertSame(existente, r.orElseThrow());
        verify(repo, never()).save(any());
    }

    @Test
    void conElAltaDesactivadaNoCreaNada() {
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.empty());

        Optional<Usuario> r = new AltaDesdeTokenService(repo, false).buscarOCrear("a@b.com", "Ana", RolUsuario.USER);

        assertTrue(r.isEmpty());
        verify(repo, never()).save(any());
    }

    @Test
    void conElAltaActivadaLoCreaConLosDatosDelToken() {
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.empty());

        Usuario nuevo = new AltaDesdeTokenService(repo, true)
                .buscarOCrear("a@b.com", "Ana Pérez", RolUsuario.USER).orElseThrow();

        assertEquals("a@b.com", nuevo.getEmail());
        assertEquals("Ana Pérez", nuevo.getNombreDisplay());
        assertEquals(AltaDesdeTokenService.PROVIDER_TOKEN_COMPARTIDO, nuevo.getProvider());
        assertEquals(RolUsuario.USER, nuevo.getRol());
        verify(repo, times(1)).save(any());
    }

    @Test
    void unAdminDeLaLigaNacionalTambienEsAdminAca() {
        when(repo.findByEmail("admin@b.com")).thenReturn(Optional.empty());

        Usuario nuevo = new AltaDesdeTokenService(repo, true)
                .buscarOCrear("admin@b.com", "Admin", RolUsuario.ADMIN).orElseThrow();

        assertEquals(RolUsuario.ADMIN, nuevo.getRol());
    }

    @Test
    void sinNombreEnElTokenUsaLaParteDelEmail() {
        when(repo.findByEmail("ana@b.com")).thenReturn(Optional.empty());

        Usuario nuevo = new AltaDesdeTokenService(repo, true)
                .buscarOCrear("ana@b.com", null, RolUsuario.USER).orElseThrow();

        assertEquals("ana", nuevo.getNombreDisplay());
    }

    @Test
    void siOtroRequestLoCreoAlMismoTiempoDevuelveEseSinFallar() {
        Usuario creadoPorElOtro = Usuario.builder().id(9L).email("a@b.com").build();
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.empty(), Optional.of(creadoPorElOtro));
        when(repo.save(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("email duplicado"));

        Optional<Usuario> r = new AltaDesdeTokenService(repo, true).buscarOCrear("a@b.com", "Ana", RolUsuario.USER);

        assertSame(creadoPorElOtro, r.orElseThrow());
    }
}
