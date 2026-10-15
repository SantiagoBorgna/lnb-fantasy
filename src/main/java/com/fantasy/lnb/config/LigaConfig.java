package com.fantasy.lnb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Liga que juega este backend. Cada liga corre su propio backend con su base;
 * lo que cambia entre una y otra se configura acá (por defecto, la Liga Nacional).
 *
 * El sitio de GES es el mismo para todas las ligas y lo que cambia es el prefijo de las rutas:
 * /laliga/... (Liga Nacional), /laligaargentina/... (Liga Argentina).
 */
@Component
public class LigaConfig {

    private final String id;
    private final String nombre;
    private final String slug;
    private final String sitioUrl;

    public LigaConfig(
            @Value("${app.liga.id:liga-nacional}") String id,
            @Value("${app.liga.nombre:Liga Nacional}") String nombre,
            @Value("${app.liga.slug:laliga}") String slug,
            @Value("${app.liga.sitio-url:https://www.laliganacional.com.ar}") String sitioUrl) {
        this.id = id;
        this.nombre = nombre;
        this.slug = slug;
        this.sitioUrl = sitioUrl.endsWith("/") ? sitioUrl.substring(0, sitioUrl.length() - 1) : sitioUrl;
    }

    public static LigaConfig ligaNacional() {
        return new LigaConfig("liga-nacional", "Liga Nacional", "laliga", "https://www.laliganacional.com.ar");
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getSlug() { return slug; }
    public String getSitioUrl() { return sitioUrl; }

    /** Ej: /laliga/partido/ */
    public String ruta(String seccion) {
        return "/" + slug + "/" + seccion + "/";
    }

    /** URL absoluta a partir de un href del sitio (relativo o absoluto) */
    public String urlAbsoluta(String href) {
        return href.startsWith("http") ? href : sitioUrl + href;
    }

    public String urlFixture() {
        return sitioUrl + "/" + slug + "/fixture";
    }
}
