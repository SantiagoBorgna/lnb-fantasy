package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.plantel.PlantelService;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto.JugadorPlantelDto;
import com.fantasy.lnb.feature.premium.dto.ConsejeroResponseDto;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PremiumService {

    private final UsuarioRepository usuarioRepository;
    private final PlantelService plantelService;

    @Scheduled(cron = "0 0 3 * * ?") // Todos los días a las 3:00 AM
    @Transactional
    public void revocarSuscripcionesVencidas() {
        log.info("[PREMIUM-CRON] Iniciando barrido de suscripciones vencidas...");
        List<Usuario> vencidos = usuarioRepository.findByIsPremiumTrueAndPremiumHastaBefore(LocalDateTime.now());
        
        for (Usuario u : vencidos) {
            u.setPremium(false);
            u.setNotificacionPremiumVencido(true);
            log.info("[PREMIUM-CRON] Suscripción vencida para usuario: {}", u.getEmail());
        }
        
        usuarioRepository.saveAll(vencidos);
        log.info("[PREMIUM-CRON] Barrido completado. Se revocaron {} suscripciones.", vencidos.size());
    }

    @Transactional
    public void marcarNotificacionVista(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.isNotificacionPremiumVencido()) {
            usuario.setNotificacionPremiumVencido(false);
            usuarioRepository.save(usuario);
        }
    }

    /**
     * Acredita el mes que cubre un pago: Premium hasta fechaPago + 1 mes.
     * Se cuenta desde la fecha del pago y no desde hoy, así un aviso repetido o tardío de Mercado Pago
     * sobre un pago viejo no regala otro mes (pasa, por ejemplo, con un usuario que pagó y canceló).
     * Nunca acorta un Premium que ya llega más lejos.
     */
    @Transactional
    public void acreditarPago(Long usuarioId, LocalDateTime fechaPago, String origen) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        LocalDateTime hasta = fechaPago.plusMonths(1);
        if (usuario.getPremiumHasta() != null && !hasta.isAfter(usuario.getPremiumHasta())) {
            log.info("[PREMIUM] {} de {} ({}) ya estaba acreditado: sigue Premium hasta {}",
                     origen, usuario.getEmail(), fechaPago, usuario.getPremiumHasta());
            return;
        }
        if (!hasta.isAfter(LocalDateTime.now())) {
            log.info("[PREMIUM] {} de {} ({}) cubre un mes que ya terminó, no se acredita",
                     origen, usuario.getEmail(), fechaPago);
            return;
        }

        usuario.setPremium(true);
        usuario.setNotificacionPremiumVencido(false);
        usuario.setPremiumHasta(hasta);
        usuarioRepository.save(usuario);

        log.info("[PREMIUM] Usuario {} ahora es Premium hasta {} ({})", usuario.getEmail(), hasta, origen);
    }

    @Transactional(readOnly = true)
    public ConsejeroResponseDto obtenerConsejos(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!usuario.isPremium()) {
            return ConsejeroResponseDto.builder()
                    .isPremium(false)
                    .advertencias(List.of())
                    .consejos(List.of())
                    .build();
        }

        List<String> advertencias = new ArrayList<>();
        List<String> consejos = new ArrayList<>();

        Optional<PlantelDto> plantelOpt = plantelService.obtenerPlantelActivo(usuarioId);
        if (plantelOpt.isEmpty()) {
            consejos.add("Aún no tienes un plantel armado. Ve al Mercado para armarlo.");
            return ConsejeroResponseDto.builder()
                    .isPremium(true)
                    .advertencias(advertencias)
                    .consejos(consejos)
                    .build();
        }

        PlantelDto plantel = plantelOpt.get();
        List<JugadorPlantelDto> jugadores = plantel.getJugadores();

        // Encontrar Capitán y Sexto Hombre
        JugadorPlantelDto capitan = null;
        JugadorPlantelDto sextoHombre = null;
        List<JugadorPlantelDto> titulares = new ArrayList<>();
        List<JugadorPlantelDto> suplentes = new ArrayList<>();

        for (JugadorPlantelDto j : jugadores) {
            if ("CAPITAN".equals(j.getRol().name())) {
                capitan = j;
            }
            if ("SEXTO_HOMBRE".equals(j.getRol().name())) {
                sextoHombre = j;
            }
            if ("TITULAR".equals(j.getRol().name()) || "CAPITAN".equals(j.getRol().name())) {
                titulares.add(j);
            } else {
                suplentes.add(j);
            }
        }

        // Análisis del Capitán
        if (capitan != null) {
            double promedioCapitan = capitan.getPromedioPuntosUltimas3();
            for (JugadorPlantelDto t : titulares) {
                if (!t.getJugadorRealId().equals(capitan.getJugadorRealId()) && t.getPromedioPuntosUltimas3() > promedioCapitan) {
                    advertencias.add(String.format("Quizás no tengas el capitán adecuado. %s (%.1f pts) viene sumando más que tu capitán %s (%.1f pts).",
                            t.getNombreCompleto(), t.getPromedioPuntosUltimas3(),
                            capitan.getNombreCompleto(), promedioCapitan));
                    break;
                }
            }
        }

        // Análisis del Sexto Hombre
        if (sextoHombre != null) {
            double promedioSexto = sextoHombre.getPromedioPuntosUltimas3();
            for (JugadorPlantelDto s : suplentes) {
                if (!s.getJugadorRealId().equals(sextoHombre.getJugadorRealId()) && s.getPromedioPuntosUltimas3() > promedioSexto) {
                    advertencias.add(String.format("Revisá tu 6to Hombre. %s (%.1f pts) viene rindiendo mejor que %s (%.1f pts).",
                            s.getNombreCompleto(), s.getPromedioPuntosUltimas3(),
                            sextoHombre.getNombreCompleto(), promedioSexto));
                    break;
                }
            }
        }

        if (advertencias.isEmpty()) {
            consejos.add("¡Tu equipo está perfectamente armado! Las elecciones de Capitán y 6to Hombre son estadísticamente correctas.");
        } else {
            consejos.add("Considerá hacer los cambios sugeridos para maximizar tus puntos en la próxima jornada.");
        }

        return ConsejeroResponseDto.builder()
                .isPremium(true)
                .advertencias(advertencias)
                .consejos(consejos)
                .build();
    }
}


