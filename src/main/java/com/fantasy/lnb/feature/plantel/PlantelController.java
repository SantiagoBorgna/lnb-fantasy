package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.plantel.dto.GuardarPlantelRequest;
import com.fantasy.lnb.feature.plantel.dto.JugadorEstadisticaDto;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaRequest;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaResultadoDto;
import com.fantasy.lnb.feature.usuario.OnboardingService;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plantel")
@RequiredArgsConstructor
public class PlantelController {

        private final PlantelService plantelService;
        private final UsuarioResolver usuarioResolver;
        private final OnboardingService onboardingService;

        /**
         * GET /api/plantel
         * Devuelve el plantel activo del usuario autenticado.
         * Si no armó plantel aún devuelve 204.
         */
        @GetMapping
        public ResponseEntity<PlantelDto> obtenerPlantel(
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return plantelService.obtenerPlantelActivo(usuarioId)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.noContent().build());
        }

        /**
         * GET /api/plantel/jornada/{jornadaId}
         * Devuelve el plantel histórico del usuario para una jornada específica.
         * Se usa en la Canchita Bimodal para ver fechas pasadas (Modo Lectura).
         */
        @GetMapping("/jornada/{jornadaId}")
        public ResponseEntity<PlantelDto> obtenerPlantelHistorico(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long jornadaId) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return plantelService.obtenerPlantelHistorico(usuarioId, jornadaId)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.noContent().build());
        }

        /**
         * POST /api/plantel
         * Arma o reemplaza el plantel para la jornada abierta.
         * Body: GuardarPlantelRequest con 10 jugadores + DT + formación.
         */
        @PostMapping
        public ResponseEntity<PlantelDto> guardarPlantel(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @Valid @RequestBody GuardarPlantelRequest request) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                PlantelDto resultado = plantelService.guardarPlantel(usuarioId, request);

                // Si el usuario estaba en PERFIL_COMPLETO, este es su primer plantel
                // → lo marcamos ACTIVO automáticamente
                onboardingService.marcarActivo(usuarioId);

                return ResponseEntity.ok(resultado);
        }

        /**
         * POST /api/plantel/transferencia
         * Reemplaza un jugador de campo por otro del mercado.
         * Consume 1 transferencia del límite de 3 por jornada.
         */
        @PostMapping("/transferencia")
        public ResponseEntity<TransferenciaResultadoDto> realizarTransferencia(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestBody TransferenciaRequest request) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return ResponseEntity.ok(
                                plantelService.realizarTransferencia(usuarioId, request));
        }

        /**
         * POST /api/plantel/dt/{nuevoDtId}
         * Cambia el DT del plantel activo.
         * Consume 1 transferencia.
         */
        @PostMapping("/dt/{nuevoDtId}")
        public ResponseEntity<TransferenciaResultadoDto> cambiarDt(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long nuevoDtId) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return ResponseEntity.ok(
                                plantelService.cambiarDt(usuarioId, nuevoDtId));
        }

        /**
         * GET /api/plantel/estadisticas/{jornadaId}
         * Devuelve los puntos Fantasy de cada jugador del plantel
         * para una jornada específica.
         * Usado por la Canchita en modo EN_JUEGO / FINALIZADA.
         */
        @GetMapping("/estadisticas/{jornadaId}")
        public ResponseEntity<List<JugadorEstadisticaDto>> estadisticasJornada(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long jornadaId) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return ResponseEntity.ok(
                                plantelService.obtenerEstadisticasJornada(usuarioId, jornadaId));
        }

        /**
         * GET /api/plantel/equipo/{equipoId}/jornada/{jornadaId}
         * Devuelve el plantel de otro jugador para una jornada específica.
         * Seguro: Solo si la jornada está EN_JUEGO o FINALIZADA.
         */
        @GetMapping("/equipo/{equipoId}/jornada/{jornadaId}")
        public ResponseEntity<PlantelDto> obtenerPlantelAjeno(
                        @PathVariable Long equipoId,
                        @PathVariable Long jornadaId) {

                return plantelService.obtenerPlantelAjeno(equipoId, jornadaId)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.noContent().build());
        }

        /**
         * GET /api/plantel/equipo/{equipoId}/estadisticas/{jornadaId}
         * Devuelve las estadísticas de otro jugador para una jornada específica.
         * Seguro: Solo si la jornada está EN_JUEGO o FINALIZADA.
         */
        @GetMapping("/equipo/{equipoId}/estadisticas/{jornadaId}")
        public ResponseEntity<List<JugadorEstadisticaDto>> obtenerEstadisticasAjenas(
                        @PathVariable Long equipoId,
                        @PathVariable Long jornadaId) {

                return ResponseEntity.ok(
                                plantelService.obtenerEstadisticasAjenas(equipoId, jornadaId));
        }
}