package com.fantasy.lnb.feature.usuario;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * El nombre del equipo virtual no se puede repetir entre usuarios, en ninguna liga.
 *
 * Se compara sin mayúsculas, tildes ni espacios de más. Cada backend revisa su base y le
 * pregunta a los de las otras ligas (app.ligas.otras-urls) por GET /api/nombres-equipo/libre.
 * El mismo usuario (mismo email) puede usar su nombre en todas las ligas.
 * Si otra liga no responde, el nombre se permite igual: una caída no frena el onboarding.
 */
@Slf4j
@Service
public class NombreEquipoService {

    static final String MENSAJE_EN_USO = "Ese nombre de equipo ya está en uso. Elegí otro.";

    private final EquipoVirtualRepository equipoVirtualRepo;
    private final List<String> otrasLigas;
    private final BiFunction<String, String, Optional<Boolean>> consultaRemota;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    public NombreEquipoService(
            EquipoVirtualRepository equipoVirtualRepo,
            @Value("${app.ligas.otras-urls:}") String otrasUrls) {
        this(equipoVirtualRepo, otrasUrls, null);
    }

    NombreEquipoService(EquipoVirtualRepository equipoVirtualRepo, String otrasUrls,
            BiFunction<String, String, Optional<Boolean>> consultaRemota) {
        this.equipoVirtualRepo = equipoVirtualRepo;
        this.otrasLigas = Arrays.stream((otrasUrls == null ? "" : otrasUrls).split(","))
                .map(String::trim)
                .filter(u -> !u.isBlank())
                .map(u -> u.replaceAll("/+$", ""))
                .toList();
        this.consultaRemota = consultaRemota;
    }

    /**
     * Lanza IllegalArgumentException si otro usuario ya usa ese nombre, en esta liga o en otra.
     * Si el nombre es el mismo que el usuario ya tiene acá, no se revisa (así los repetidos
     * de antes del control pueden seguir editando el resto de su perfil).
     */
    public void validarDisponible(String nombre, String email, String nombreActual) {
        if (nombreActual != null && normalizar(nombreActual).equals(normalizar(nombre))) return;

        if (!libreEnEstaLiga(nombre, email)) {
            throw new IllegalArgumentException(MENSAJE_EN_USO);
        }
        for (String url : otrasLigas) {
            Optional<Boolean> libre = consultaRemota != null
                    ? consultaRemota.apply(url, nombre)
                    : consultar(url, nombre, email);
            if (libre.isPresent() && !libre.get()) {
                throw new IllegalArgumentException(MENSAJE_EN_USO);
            }
        }
    }

    /** true si ningún usuario distinto de {@code email} usa ese nombre en esta liga. */
    public boolean libreEnEstaLiga(String nombre, String email) {
        String buscado = normalizar(nombre);
        return equipoVirtualRepo.findNombresConEmail().stream()
                .noneMatch(fila -> buscado.equals(normalizar((String) fila[0]))
                        && !((String) fila[1]).equalsIgnoreCase(email));
    }

    static String normalizar(String nombre) {
        if (nombre == null) return "";
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private Optional<Boolean> consultar(String url, String nombre, String email) {
        try {
            String query = "nombre=" + URLEncoder.encode(nombre, StandardCharsets.UTF_8)
                    + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/api/nombres-equipo/libre?" + query))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[NOMBRE] {} respondió {} al revisar un nombre de equipo", url, res.statusCode());
                return Optional.empty();
            }
            return Optional.of(JSON.readTree(res.body()).path("libre").asBoolean(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[NOMBRE] No se pudo revisar el nombre en {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
