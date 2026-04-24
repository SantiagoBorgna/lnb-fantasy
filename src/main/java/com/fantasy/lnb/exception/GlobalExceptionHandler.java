package com.fantasy.lnb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;

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
         * Evita que stacktraces internos lleguen al cliente.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {

                log.error("[API] Error no manejado: {}", ex.getMessage(), ex);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 500,
                                "error", "Error interno del servidor"));
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
}