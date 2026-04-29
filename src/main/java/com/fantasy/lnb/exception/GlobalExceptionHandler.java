package com.fantasy.lnb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * Captura el caso de pasar una posición inválida como query param.
         * Sin este handler Spring devuelve un 500 genérico poco informativo.
         */
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Map<String, Object>> handleEnumMismatch(
                        MethodArgumentTypeMismatchException ex) {

                log.warn("[API] Parámetro inválido: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 400,
                                "error", "Parámetro inválido",
                                "mensaje", "Valor '" + ex.getValue() + "' no es válido para '"
                                                + ex.getName() + "'"));
        }

        /**
         * Handler genérico de último recurso.
         * FIX: Ahora expone el mensaje real para que React lo pueda leer.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
                log.error("[API] Error no manejado: {}", ex.getMessage(), ex);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 500,
                                "error", "Error interno del servidor",
                                "mensaje", ex.getMessage() != null ? ex.getMessage() : "Error interno desconocido"));
        }

        /**
         * Captura errores de estado inválido (muy comunes en lógicas de negocio).
         */
        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "status", 409,
                                "error", "Conflicto de estado",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(PresupuestoInsuficienteException.class)
        public ResponseEntity<Map<String, Object>> handlePresupuesto(
                        PresupuestoInsuficienteException ex) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "status", 422,
                                "error", "Presupuesto insuficiente",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(FormacionInvalidaException.class)
        public ResponseEntity<Map<String, Object>> handleFormacion(
                        FormacionInvalidaException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                                "status", 400,
                                "error", "Formación inválida",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(JornadaEnJuegoException.class)
        public ResponseEntity<Map<String, Object>> handleJornadaEnJuego(
                        JornadaEnJuegoException ex) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "status", 409,
                                "error", "Jornada en juego",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(TransferenciasAgotadasException.class)
        public ResponseEntity<Map<String, Object>> handleTransferencias(
                        TransferenciasAgotadasException ex) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "status", 422,
                                "error", "Transferencias agotadas",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(PlantelIncompletoException.class)
        public ResponseEntity<Map<String, Object>> handlePlantelIncompleto(
                        PlantelIncompletoException ex) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "status", 422,
                                "error", "Plantel incompleto",
                                "mensaje", ex.getMessage()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", 400);
                response.put("error", "Parámetro inválido");
                response.put("mensaje", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidacion(
                        MethodArgumentNotValidException ex) {

                // Agrupa todos los errores de campo en un solo mensaje legible
                String errores = ex.getBindingResult().getFieldErrors()
                                .stream()
                                .map(FieldError::getDefaultMessage)
                                .collect(Collectors.joining(" | "));

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                                "status", 400,
                                "error", "Error de validación",
                                "mensaje", errores));
        }
}