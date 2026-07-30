package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.torneo.dto.CrearTorneoRequest;
import com.fantasy.lnb.feature.torneo.dto.EditarTorneoRequest;
import com.fantasy.lnb.feature.torneo.dto.EnfrentamientoDto;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import com.fantasy.lnb.feature.torneo.dto.TorneoDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/torneos")
@RequiredArgsConstructor
public class TorneoController {

        private final TorneoService torneoService;
        private final UsuarioResolver usuarioResolver;
        private final TorneoRepository torneoRepo;

        // GET /api/torneos?nombre=xxx — buscador de torneos públicos
        @GetMapping
        public ResponseEntity<List<TorneoDto>> listarPublicos(
                        @RequestParam(required = false) String nombre) {
                return ResponseEntity.ok(torneoService.listarPublicos(nombre));
        }

        // GET /api/torneos/mis-torneos — torneos del usuario autenticado
        @GetMapping("/mis-torneos")
        public ResponseEntity<List<TorneoDto>> misTorneos(
                        @AuthenticationPrincipal UserDetails userDetails) {
                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                return ResponseEntity.ok(torneoService.listarMisTorneos(usuarioId));
        }

        // GET /api/torneos/{id}/tabla — tabla de posiciones de un torneo
        @GetMapping("/{id}/tabla")
        public ResponseEntity<List<PosicionTorneoDto>> tabla(
                        @PathVariable Long id) {
                return ResponseEntity.ok(torneoService.obtenerTablaPosiciones(id));
        }

        // GET /api/torneos/{id}/fixture - fixture H2H del torneo
        @GetMapping("/{id}/fixture")
        public ResponseEntity<List<EnfrentamientoDto>> fixture(
                        @PathVariable Long id) {
                return ResponseEntity.ok(torneoService.obtenerFixtureH2H(id));
        }

        /**
         * GET /api/torneos/{id}
         * Detalle de un torneo específico.
         * Incluye si el usuario autenticado es el creador (para mostrar ajustes).
         */
        @GetMapping("/{id}")
        public ResponseEntity<TorneoDto> obtenerTorneo(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long usuarioId = userDetails != null
                                ? usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername())
                                : null;

                return torneoRepo.findById(id)
                                .map(torneo -> {
                                        TorneoDto dto = torneoService.toDtoConPermisos(torneo, usuarioId);
                                        return ResponseEntity.ok(dto);
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        @GetMapping("/codigo/{codigo}")
        public ResponseEntity<TorneoDto> obtenerPorCodigo(@PathVariable String codigo) {
                return ResponseEntity.ok(torneoService.obtenerTorneoPorCodigo(codigo));
        }

        // POST /api/torneos — crear un torneo nuevo
        @PostMapping
        public ResponseEntity<TorneoDto> crear(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @Valid @RequestBody CrearTorneoRequest request) {
                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                return ResponseEntity.ok(torneoService.crearTorneo(usuarioId, request));
        }

        // POST /api/torneos/unirse/{codigo} — unirse por UUID
        @PostMapping("/unirse/{codigo}")
        public ResponseEntity<TorneoDto> unirse(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable String codigo) {
                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                return ResponseEntity.ok(
                                torneoService.unirseATorneo(usuarioId, codigo));
        }

        /**
         * DELETE /api/torneos/{id}
         * El creador elimina el torneo permanentemente.
         */
        @DeleteMapping("/{id}")
        public ResponseEntity<?> eliminarTorneo(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                torneoService.eliminarTorneo(id, usuarioId);
                return ResponseEntity.ok(Map.of("mensaje", "Torneo eliminado con éxito."));
        }

        /**
         * DELETE /api/torneos/{id}/salir
         * El usuario autenticado sale del torneo.
         * El creador no puede salir — debe eliminar el torneo.
         */
        @DeleteMapping("/{id}/salir")
        public ResponseEntity<?> salirDeTorneo(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());

                torneoService.salirDeTorneo(id, usuarioId);
                return ResponseEntity.ok(Map.of("mensaje", "Saliste del torneo."));
        }

        /**
         * PATCH /api/torneos/{id}/ajustes
         * Edita nombre, descripción y tipo. Solo el admin del torneo.
         */
        @PatchMapping("/{id}/ajustes")
        public ResponseEntity<TorneoDto> editarTorneo(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestBody EditarTorneoRequest request) {

                Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                return ResponseEntity.ok(
                                torneoService.editarTorneo(id, usuarioId, request));
        }

        @DeleteMapping("/{id}/participantes/{equipoVirtualId}")
        public ResponseEntity<?> expulsarParticipante(
                        @PathVariable Long id,
                        @PathVariable Long equipoVirtualId,
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                torneoService.expulsarParticipante(id, adminId, equipoVirtualId);
                return ResponseEntity.ok(Map.of("mensaje", "Participante expulsado."));
        }

        /**
         * POST /api/torneos/{id}/bot
         * Agrega un bot de prueba al torneo. Solo el admin.
         */
        @PostMapping("/{id}/bot")
        public ResponseEntity<TorneoDto> agregarBot(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = usuarioResolver.resolverIdDesdeEmail(
                                userDetails.getUsername());
                return ResponseEntity.ok(torneoService.agregarBot(id, adminId));
        }
}