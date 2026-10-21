package com.fantasy.lnb.feature.usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NombreEquipoServiceTest {

    private EquipoVirtualRepository repo;
    private final List<String> consultadas = new ArrayList<>();
    private final AtomicReference<Optional<Boolean>> respuestaRemota = new AtomicReference<>(Optional.of(true));

    @BeforeEach
    void setUp() {
        repo = mock(EquipoVirtualRepository.class);
        when(repo.findNombresConEmail()).thenReturn(List.of(
                new Object[] { "Los Pibes", "otro@mail.com" },
                new Object[] { "Mío", "yo@mail.com" }));
    }

    private NombreEquipoService servicio(String otrasUrls) {
        return new NombreEquipoService(repo, otrasUrls, (url, nombre) -> {
            consultadas.add(url);
            return respuestaRemota.get();
        });
    }

    @Test
    void rechazaUnNombreQueUsaOtroUsuarioAunqueCambienMayusculasTildesYEspacios() {
        NombreEquipoService s = servicio("");

        var ex = assertThrows(IllegalArgumentException.class,
                () -> s.validarDisponible("  lós   PIBES ", "yo@mail.com", null));
        assertEquals(NombreEquipoService.MENSAJE_EN_USO, ex.getMessage());
    }

    @Test
    void aceptaUnNombreLibre() {
        assertDoesNotThrow(() -> servicio("").validarDisponible("Los Pibes 2", "yo@mail.com", null));
    }

    @Test
    void elMismoUsuarioPuedeRepetirSuPropioNombre() {
        assertTrue(servicio("").libreEnEstaLiga("mio", "YO@mail.com"));
    }

    @Test
    void siNoCambiaElNombreNoSeRevisaAunqueEsteRepetido() {
        NombreEquipoService s = servicio("https://otra.example.com");

        assertDoesNotThrow(() -> s.validarDisponible("Los Pibes", "otro2@mail.com", "los pibes"));
        verifyNoInteractions(repo);
        assertTrue(consultadas.isEmpty());
    }

    @Test
    void rechazaSiLoUsaAlguienEnOtraLiga() {
        respuestaRemota.set(Optional.of(false));
        NombreEquipoService s = servicio("https://otra.example.com/");

        assertThrows(IllegalArgumentException.class,
                () -> s.validarDisponible("Nuevo", "yo@mail.com", null));
        assertEquals(List.of("https://otra.example.com"), consultadas);
    }

    @Test
    void siLaOtraLigaNoRespondeSePermite() {
        respuestaRemota.set(Optional.empty());

        assertDoesNotThrow(() -> servicio("https://otra.example.com").validarDisponible("Nuevo", "yo@mail.com", null));
    }

    @Test
    void sinOtrasLigasConfiguradasNoConsultaAfuera() {
        servicio(" ").validarDisponible("Nuevo", "yo@mail.com", null);

        assertTrue(consultadas.isEmpty());
    }
}
