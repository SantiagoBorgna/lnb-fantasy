package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Un solo Premium para todas las ligas: se compra en la Liga Nacional y los demás backends
 * (con app.premium.fuente-url apuntando al backend de la Liga Nacional) se lo preguntan,
 * reenviando el token del usuario, y copian el resultado en su base.
 *
 * Así el resto del código sigue usando usuario.isPremium() sin cambios.
 * Se consulta como mucho una vez cada {@link #CADA_MS} por usuario.
 */
@Slf4j
@Service
public class PremiumCompartidoService {

    static final long CADA_MS = Duration.ofMinutes(5).toMillis();

    private final UsuarioRepository usuarioRepository;
    private final String fuenteUrl;
    private final Function<String, Optional<Boolean>> consulta;
    private final LongSupplier reloj;
    private final Map<String, Long> ultimaConsulta = new ConcurrentHashMap<>();

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    public PremiumCompartidoService(
            UsuarioRepository usuarioRepository,
            @Value("${app.premium.fuente-url:}") String fuenteUrl) {
        this(usuarioRepository, fuenteUrl, null, System::currentTimeMillis);
    }

    PremiumCompartidoService(UsuarioRepository usuarioRepository, String fuenteUrl,
            Function<String, Optional<Boolean>> consulta, LongSupplier reloj) {
        this.usuarioRepository = usuarioRepository;
        this.fuenteUrl = fuenteUrl == null ? "" : fuenteUrl.replaceAll("/+$", "");
        this.consulta = consulta != null ? consulta : this::consultarFuente;
        this.reloj = reloj;
    }

    /** true si el Premium de este backend viene de otra liga (y entonces acá no se vende). */
    public boolean esCompartido() {
        return !fuenteUrl.isBlank();
    }

    /** Actualiza usuario.isPremium con lo que dice la Liga Nacional (si hace falta). */
    public void sincronizar(Usuario usuario, String token) {
        if (!esCompartido()) return;

        long ahora = reloj.getAsLong();
        Long ultima = ultimaConsulta.get(usuario.getEmail());
        if (ultima != null && ahora - ultima < CADA_MS) return;
        ultimaConsulta.put(usuario.getEmail(), ahora);

        Optional<Boolean> premium = consulta.apply(token);
        if (premium.isEmpty()) return; // si la Liga Nacional no responde, dejamos lo que había

        if (premium.get() != usuario.isPremium()) {
            usuario.setPremium(premium.get());
            usuarioRepository.save(usuario);
            log.info("[PREMIUM] {} ahora {} es Premium (según la Liga Nacional)",
                    usuario.getEmail(), premium.get() ? "sí" : "no");
        }
    }

    private Optional<Boolean> consultarFuente(String token) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(fuenteUrl + "/api/auth/me"))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[PREMIUM] La Liga Nacional respondió {} al consultar el Premium", res.statusCode());
                return Optional.empty();
            }
            JsonNode body = JSON.readTree(res.body());
            return Optional.of(body.path("isPremium").asBoolean(false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[PREMIUM] No se pudo consultar el Premium en la Liga Nacional: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
