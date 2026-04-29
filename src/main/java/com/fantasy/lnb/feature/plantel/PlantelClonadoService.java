package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantelClonadoService {

    private final PlantelJornadaRepository plantelRepo;
    private final JornadaRepository jornadaRepo;

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
        int omitidos = 0;

        for (PlantelJornada origen : planteles) {
            Long usuarioId = origen.getUsuario().getId();

            // Idempotencia: no clonar si ya existe plantel en la jornada destino
            if (plantelRepo.existsByUsuario_IdAndJornada_Id(
                    usuarioId, jornadaDestino.getId())) {
                log.debug("[CLONADO] Usuario {} ya tiene plantel en J{}. Omitido.",
                        usuarioId, jornadaDestino.getNumero());
                omitidos++;
                continue;
            }

            PlantelJornada clon = PlantelJornada.builder()
                    .usuario(origen.getUsuario())
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

        log.info("[CLONADO] J{} a J{} | Clonados: {} | Omitidos: {}",
                jornadaOrigen.getNumero(),
                jornadaDestino.getNumero(),
                clones.size(),
                omitidos);

        return clones.size();
    }

    /**
     * Resuelve automáticamente qué jornadas usar:
     * busca la jornada recién finalizada y la nueva jornada abierta.
     * Devuelve -1 si no se puede resolver el par.
     */
    @Transactional
    public int clonarJornadaFinalizadaHaciaAbierta() {
        Jornada jornadaAbierta = jornadaRepo
                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElse(null);

        if (jornadaAbierta == null) {
            log.warn("[CLONADO] No hay jornada ABIERTA_A_CAMBIOS para clonar.");
            return -1;
        }

        // La jornada origen es la finalizada inmediatamente anterior
        Jornada jornadaAnterior = jornadaRepo
                .findFirstByEstadoAndNumeroLessThanOrderByNumeroDesc(
                        EstadoJornada.FINALIZADA,
                        jornadaAbierta.getNumero())
                .orElse(null);

        if (jornadaAnterior == null) {
            log.warn("[CLONADO] No hay jornada FINALIZADA anterior a J{}.",
                    jornadaAbierta.getNumero());
            return -1;
        }

        return clonarPlantelesHaciaJornada(jornadaAnterior, jornadaAbierta);
    }
}