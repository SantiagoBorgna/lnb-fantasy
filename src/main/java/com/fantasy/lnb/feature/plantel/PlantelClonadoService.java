package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantelClonadoService {

        private final PlantelJornadaRepository plantelRepo;
        private final JornadaRepository jornadaRepo;
        private final TorneoRepository torneoRepo;

        /**
         * Clona masivamente todos los planteles de jornadaOrigen
         * hacia jornadaDestino.
         *
         * Reglas:
         * - Solo clona usuarios que NO tienen plantel en jornadaDestino
         * (idempotente — se puede llamar más de una vez sin duplicar)
         * - Preserva: jugadores, roles, capitán, formación, DT
         * - Resetea: transferenciasUsadas → 0, puntajeObtenidoFecha → 0
         *
         * @return cantidad de planteles clonados
         */
        @Transactional
        public int clonarPlantelesHaciaJornada(Jornada jornadaOrigen,
                        Jornada jornadaDestino) {

                List<PlantelJornada> planteles = plantelRepo
                                .findAllByJornadaIdWithJugadores(jornadaOrigen.getId());

                if (planteles.isEmpty()) {
                        log.warn("[CLONADO] No hay planteles en jornada {} para clonar.",
                                        jornadaOrigen.getNumero());
                        return 0;
                }

                List<PlantelJornada> clones = new ArrayList<>();
                List<PlantelJornada> plantelesHuerfanos = new ArrayList<>();
                Map<Long, Boolean> cacheTorneos = new HashMap<>();
                int omitidos = 0;

                for (PlantelJornada origen : planteles) {
                        Long usuarioId = origen.getUsuario().getId();

                        // Si el torneo ya no existe, registramos el plantel para eliminarlo y saltamos la clonacin
                        if (origen.getTorneo() != null) {
                                Long torneoId = origen.getTorneo().getId();
                                boolean torneoExiste = cacheTorneos.computeIfAbsent(torneoId, id -> torneoRepo.existsById(id));
                                if (!torneoExiste) {
                                        log.warn("[CLONADO] Torneo {} ya no existe. El plantel {} serǭ eliminado.", torneoId, origen.getId());
                                        plantelesHuerfanos.add(origen);
                                        continue;
                                }
                        }

                        // Idempotencia: no clonar si ya existe plantel en la jornada destino
                        boolean existe;
                        if (origen.getTorneo() == null) {
                                existe = plantelRepo.existsByUsuario_IdAndJornada_IdAndTorneoIsNull(
                                                usuarioId, jornadaDestino.getId());
                        } else {
                                existe = plantelRepo.existsByUsuario_IdAndJornada_IdAndTorneo_Id(
                                                usuarioId, jornadaDestino.getId(), origen.getTorneo().getId());
                        }
                        
                        if (existe) {
                                log.debug("[CLONADO] Usuario {} ya tiene plantel (Torneo: {}) en J{}. Omitido.",
                                                usuarioId, origen.getTorneo() != null ? origen.getTorneo().getId() : "GLOBAL", jornadaDestino.getNumero());
                                omitidos++;
                                continue;
                        }

                        PlantelJornada clon = PlantelJornada.builder()
                                        .usuario(origen.getUsuario())
                                        .torneo(origen.getTorneo())
                                        .jornada(jornadaDestino)
                                        .dt(origen.getDt())
                                        .formacion(origen.getFormacion())
                                        .puntajeObtenidoFecha(0.0)
                                        .transferenciasUsadas(0)
                                        .build();

                        // Clonar cada slot preservando rol y capitanía
                        List<JugadorPlantel> slots = origen.getJugadores().stream()
                                        .map(jp -> JugadorPlantel.builder()
                                                        .plantelJornada(clon)
                                                        .jugadorReal(jp.getJugadorReal())
                                                        .rol(jp.getRol())
                                                        .precioDeCompra(jp.getJugadorReal()
                                                                        .getValorMercadoActual()) // precio actualizado
                                                        .build())
                                        .collect(java.util.stream.Collectors.toList());

                        clon.setJugadores(slots);
                        clones.add(clon);
                }

                // Guardar todos en batch para eficiencia
                plantelRepo.saveAll(clones);

                // Eliminar planteles hurfanos que pertenezcan a torneos eliminados
                if (!plantelesHuerfanos.isEmpty()) {
                        plantelRepo.deleteAll(plantelesHuerfanos);
                        log.info("[CLONADO] Se eliminaron {} planteles hurfanos de torneos borrados.", plantelesHuerfanos.size());
                }

                log.info("[CLONADO] J{} a J{} | Clonados: {} | Omitidos: {}",
                                jornadaOrigen.getNumero(),
                                jornadaDestino.getNumero(),
                                clones.size(),
                                omitidos);

                return clones.size();
        }

        /**
         * Clona los planteles desde una jornada que acaba de finalizar
         * hacia la próxima jornada disponible (ABIERTA_A_CAMBIOS).
         */
        @Transactional
        public int clonarDesdeJornadaFinalizada(Jornada jornadaRecienFinalizada) {

                // Buscamos la jornada destino (la que le sigue cronológicamente)
                Jornada jornadaDestino = jornadaRepo
                                .findFirstByEstadoAndNumeroGreaterThanOrderByNumeroAsc(
                                                EstadoJornada.ABIERTA_A_CAMBIOS,
                                                jornadaRecienFinalizada.getNumero())
                                .orElse(null);

                if (jornadaDestino == null) {
                        log.warn("[CLONADO] No hay próxima jornada ABIERTA para clonar desde J{}.",
                                        jornadaRecienFinalizada.getNumero());
                        return -1;
                }

                return clonarPlantelesHaciaJornada(jornadaRecienFinalizada, jornadaDestino);
        }
}