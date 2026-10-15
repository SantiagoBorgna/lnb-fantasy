package com.fantasy.lnb.config;

import com.fantasy.lnb.feature.premium.PremiumCompartidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Datos públicos de la liga que juega este backend (los usa el frontend y el admin). */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final LigaConfig liga;
    private final PremiumCompartidoService premiumCompartido;

    @GetMapping
    public Map<String, Object> config() {
        return Map.of(
                "ligaId", liga.getId(),
                "ligaNombre", liga.getNombre(),
                "sitioUrl", liga.getSitioUrl(),
                "urlFixture", liga.urlFixture(),
                "premiumCompartido", premiumCompartido.esCompartido());
    }
}
