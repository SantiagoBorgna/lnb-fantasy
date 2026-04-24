package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.plantel.dto.GuardarPlantelRequest;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaRequest;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaResultadoDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

                return ResponseEntity.ok(
                                plantelService.guardarPlantel(usuarioId, request));
        }

        /**
         * PATCH /api/plantel/transferencia
         * Reemplaza un jugador de campo por otro del mercado.
         * Consume 1 transferencia del límite de 3 por jornada.
         */
        @PatchMapping("/transferencia")
        public ResponseEntity<TransferenciaResultadoDto> realizarTransferencia(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestBody TransferenciaRequest request) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return ResponseEntity.ok(
                                plantelService.realizarTransferencia(usuarioId, request));
        }

        /**
         * PATCH /api/plantel/dt/{nuevoDtId}
         * Cambia el DT del plantel activo.
         * Consume 1 transferencia.
         */
        @PatchMapping("/dt/{nuevoDtId}")
        public ResponseEntity<TransferenciaResultadoDto> cambiarDt(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long nuevoDtId) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                return ResponseEntity.ok(
                                plantelService.cambiarDt(usuarioId, nuevoDtId));
        }
}