package com.fantasy.lnb.feature.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lo consultan los backends de las otras ligas antes de aceptar un nombre de equipo. */
@RestController
@RequestMapping("/api/nombres-equipo")
@RequiredArgsConstructor
public class NombreEquipoController {

    private final NombreEquipoService nombreEquipoService;

    @GetMapping("/libre")
    public Map<String, Boolean> libre(@RequestParam String nombre, @RequestParam String email) {
        return Map.of("libre", nombreEquipoService.libreEnEstaLiga(nombre, email));
    }
}
