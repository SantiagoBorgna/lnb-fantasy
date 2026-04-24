package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.exception.FormacionInvalidaException;
import com.fantasy.lnb.exception.JornadaEnJuegoException;
import com.fantasy.lnb.exception.PresupuestoInsuficienteException;
import com.fantasy.lnb.exception.TransferenciasAgotadasException;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.dto.GuardarPlantelRequest;
import com.fantasy.lnb.feature.plantel.dto.PlantelDto;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaRequest;
import com.fantasy.lnb.feature.plantel.dto.TransferenciaResultadoDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantelService {

        private final PlantelJornadaRepository plantelRepo;
        private final JugadorRealRepository jugadorRepo;
        private final JornadaRepository jornadaRepo;
        private final UsuarioRepository usuarioRepo;
        private final EquipoVirtualRepository equipoVirtualRepo;
        private final JugadorPlantelRepository jugadorPlantelRepo;

        // ── Consulta ────────────────────────────────────────────────────────────

        /**
         * Devuelve el plantel del usuario para la jornada activa.
         * Si no armó plantel aún, devuelve empty.
         */
        public Optional<PlantelDto> obtenerPlantelActivo(Long usuarioId) {
                return jornadaRepo.findByEstado(EstadoJornada.EN_JUEGO)
                                .or(() -> jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(
                                                EstadoJornada.ABIERTA_A_CAMBIOS))
                                .flatMap(jornada -> plantelRepo.findByUsuario_IdAndJornada_Id(
                                                usuarioId, jornada.getId()))
                                .map(plantel -> toDto(plantel, obtenerPresupuesto(usuarioId)));
        }

        // ── Armado del plantel ──────────────────────────────────────────────────

        /**
         * Guarda o reemplaza el plantel del usuario para la jornada activa.
         *
         * Validaciones en orden:
         * 1. Existe jornada disponible (ABIERTA o EN_JUEGO)
         * 2. La jornada NO está EN_JUEGO (no se permiten cambios)
         * 3. Exactamente 10 jugadores + 1 DT
         * 4. Exactamente 1 CAPITAN y 1 SEXTO_HOMBRE
         * 5. Formación válida y compatible con posiciones de titulares
         * 6. Presupuesto suficiente
         */
        @Transactional
        public PlantelDto guardarPlantel(Long usuarioId,
                        GuardarPlantelRequest request) {

                // ── 1. Resolver jornada ──────────────────────────────────────────
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(() -> new IllegalStateException(
                                                "No hay jornada abierta para armar el plantel."));

                // ── 2. Bloquear si está EN_JUEGO ─────────────────────────────────
                if (jornada.estaEnJuego()) {
                        throw new JornadaEnJuegoException();
                }

                // ── 3. Validar cantidad de jugadores ─────────────────────────────
                if (request.getJugadores() == null || request.getJugadores().size() != 10) {
                        throw new IllegalArgumentException(
                                        "El plantel debe tener exactamente 10 jugadores.");
                }

                // ── 4. Validar roles únicos ──────────────────────────────────────
                validarRolesUnicos(request.getJugadores());

                // ── 5. Cargar entidades de jugadores ─────────────────────────────
                List<JugadorReal> jugadoresReales = cargarJugadores(request.getJugadores());
                JugadorReal dt = jugadorRepo.findById(request.getDtId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "DT no encontrado: " + request.getDtId()));

                // ── 6. Validar formación ─────────────────────────────────────────
                List<PosicionJugador> posicionesTitulares = obtenerPosicionesTitulares(
                                request.getJugadores(), jugadoresReales);

                if (!FormacionValidator.esValida(request.getFormacion(), posicionesTitulares)) {
                        throw new FormacionInvalidaException(request.getFormacion());
                }

                // ── 7. Validar presupuesto ───────────────────────────────────────
                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Equipo virtual no encontrado para usuario: " + usuarioId));

                double costoTotal = calcularCostoTotal(jugadoresReales);
                if (equipo.getPresupuestoActual() < costoTotal) {
                        throw new PresupuestoInsuficienteException(
                                        equipo.getPresupuestoActual(), costoTotal);
                }

                // ── 8. Crear o reemplazar el plantel ─────────────────────────────
                Usuario usuario = usuarioRepo.findById(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Usuario no encontrado: " + usuarioId));

                // Si ya existe un plantel para esta jornada, lo eliminamos y recreamos
                plantelRepo.findByUsuario_IdAndJornada_Id(usuarioId, jornada.getId())
                                .ifPresent(plantelRepo::delete);

                PlantelJornada plantel = PlantelJornada.builder()
                                .usuario(usuario)
                                .jornada(jornada)
                                .dt(dt)
                                .formacion(request.getFormacion())
                                .build();

                // Construir los JugadorPlantel con precio congelado al momento de compra
                List<JugadorPlantel> slots = new ArrayList<>();
                for (int i = 0; i < request.getJugadores().size(); i++) {
                        GuardarPlantelRequest.SlotJugadorRequest slot = request.getJugadores().get(i);
                        JugadorReal jugador = jugadoresReales.get(i);

                        slots.add(JugadorPlantel.builder()
                                        .plantelJornada(plantel)
                                        .jugadorReal(jugador)
                                        .rol(slot.getRol())
                                        .precioDeCompra(jugador.getValorMercadoActual())
                                        .build());
                }

                plantel.setJugadores(slots);
                PlantelJornada guardado = plantelRepo.save(plantel);

                // ── 9. Descontar presupuesto ─────────────────────────────────────
                equipo.setPresupuestoActual(equipo.getPresupuestoActual() - costoTotal);
                equipoVirtualRepo.save(equipo);

                log.info("[PLANTEL] Usuario {} armó plantel para jornada {}. " +
                                "Costo: {} | Presupuesto restante: {}",
                                usuarioId, jornada.getNumero(),
                                costoTotal, equipo.getPresupuestoActual());

                return toDto(guardado, equipo.getPresupuestoActual());
        }

        // ── Helpers de validación ───────────────────────────────────────────────

        private void validarRolesUnicos(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots) {

                long capitanes = slots.stream()
                                .filter(s -> s.getRol() == RolPlantel.CAPITAN).count();
                long sextosHombre = slots.stream()
                                .filter(s -> s.getRol() == RolPlantel.SEXTO_HOMBRE).count();

                if (capitanes != 1) {
                        throw new IllegalArgumentException(
                                        "El plantel debe tener exactamente 1 Capitán.");
                }
                if (sextosHombre != 1) {
                        throw new IllegalArgumentException(
                                        "El plantel debe tener exactamente 1 Sexto Hombre.");
                }
        }

        private List<JugadorReal> cargarJugadores(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots) {

                return slots.stream()
                                .map(slot -> jugadorRepo.findById(slot.getJugadorRealId())
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Jugador no encontrado: " + slot.getJugadorRealId())))
                                .toList();
        }

        private List<PosicionJugador> obtenerPosicionesTitulares(
                        List<GuardarPlantelRequest.SlotJugadorRequest> slots,
                        List<JugadorReal> jugadores) {

                List<PosicionJugador> posiciones = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                        RolPlantel rol = slots.get(i).getRol();
                        if (rol == RolPlantel.TITULAR || rol == RolPlantel.CAPITAN) {
                                posiciones.add(jugadores.get(i).getPosicion());
                        }
                }
                return posiciones;
        }

        private double calcularCostoTotal(List<JugadorReal> jugadores) {
                return jugadores.stream()
                                .mapToDouble(JugadorReal::getValorMercadoActual)
                                .sum();
        }

        private double obtenerPresupuesto(Long usuarioId) {
                return equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .map(EquipoVirtual::getPresupuestoActual)
                                .orElse(0.0);
        }

        // ── Mapper ──────────────────────────────────────────────────────────────

        public PlantelDto toDto(PlantelJornada plantel, double presupuestoRestante) {

                List<PlantelDto.JugadorPlantelDto> jugadoresDto = plantel.getJugadores()
                                .stream()
                                .map(jp -> PlantelDto.JugadorPlantelDto.builder()
                                                .jugadorPlantelId(jp.getId())
                                                .jugadorRealId(jp.getJugadorReal().getId())
                                                .nombreCompleto(jp.getJugadorReal().getNombreCompleto())
                                                .numeroCamiseta(jp.getJugadorReal().getNumeroCamiseta())
                                                .equipoSigla(jp.getJugadorReal().getEquipoReal().getSigla())
                                                .colorPrincipal(jp.getJugadorReal().getEquipoReal().getColorPrincipal())
                                                .colorSecundario(jp.getJugadorReal().getEquipoReal()
                                                                .getColorSecundario())
                                                .posicion(jp.getJugadorReal().getPosicion())
                                                .estado(jp.getJugadorReal().getEstado())
                                                .rol(jp.getRol())
                                                .multiplicador(jp.getMultiplicador())
                                                .precioDeCompra(jp.getPrecioDeCompra())
                                                .valorMercadoActual(jp.getJugadorReal().getValorMercadoActual())
                                                .build())
                                .toList();

                PlantelDto.JugadorPlantelDto dtDto = plantel.getDt() != null
                                ? PlantelDto.JugadorPlantelDto.builder()
                                                .jugadorRealId(plantel.getDt().getId())
                                                .nombreCompleto(plantel.getDt().getNombreCompleto())
                                                .equipoSigla(plantel.getDt().getEquipoReal().getSigla())
                                                .build()
                                : null;

                return PlantelDto.builder()
                                .plantelId(plantel.getId())
                                .jornadaNumero(plantel.getJornada().getNumero())
                                .formacion(plantel.getFormacion())
                                .puntajeObtenidoFecha(plantel.getPuntajeObtenidoFecha())
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .dt(dtDto)
                                .jugadores(jugadoresDto)
                                .presupuestoRestante(presupuestoRestante)
                                .build();
        }

        // ── Transferencias ──────────────────────────────────────────────────────────

        /**
         * Reemplaza un jugador del plantel por otro del mercado.
         *
         * Reglas del PRD:
         * - Máximo 3 transferencias por jornada
         * - No se puede transferir con la jornada EN_JUEGO
         * - Cambiar el DT consume 1 transferencia
         * - El presupuesto se ajusta por la diferencia de precios
         * - El jugador entrante se compra al precio actual del mercado
         * - El jugador saliente se "vende" al precio que fue comprado
         */
        @Transactional
        public TransferenciaResultadoDto realizarTransferencia(
                        Long usuarioId,
                        TransferenciaRequest request) {

                // ── 1. Verificar que la jornada permita cambios ──────────────────────
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(JornadaEnJuegoException::new);

                // ── 2. Cargar plantel activo ─────────────────────────────────────────
                PlantelJornada plantel = plantelRepo
                                .findByUsuario_IdAndJornada_Id(usuarioId, jornada.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No tenés plantel armado para la jornada activa."));

                // ── 3. Verificar transferencias disponibles ──────────────────────────
                if (!plantel.puedeHacerTransferencia()) {
                        throw new TransferenciasAgotadasException();
                }

                // ── 4. Localizar el slot del jugador que sale ────────────────────────
                JugadorPlantel slotSaliente = plantel.getJugadores().stream()
                                .filter(jp -> jp.getJugadorReal().getId().equals(request.getJugadorSaleId()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "El jugador que sale no está en tu plantel."));

                // ── 5. Cargar el jugador entrante ────────────────────────────────────
                JugadorReal jugadorEntra = jugadorRepo
                                .findById(request.getJugadorEntraId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Jugador entrante no encontrado: " + request.getJugadorEntraId()));

                // ── 6. Validar que el entrante no esté ya en el plantel ──────────────
                boolean yaEstaEnPlantel = plantel.getJugadores().stream()
                                .anyMatch(jp -> jp.getJugadorReal().getId()
                                                .equals(request.getJugadorEntraId()));
                if (yaEstaEnPlantel) {
                        throw new IllegalArgumentException(
                                        jugadorEntra.getNombreCompleto() + " ya está en tu plantel.");
                }

                // ── 7. Ajuste de presupuesto ─────────────────────────────────────────
                // Se recupera lo que se pagó al comprar al saliente (precio congelado)
                // Se descuenta el precio actual del entrante
                EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Equipo virtual no encontrado."));

                double precioVenta = slotSaliente.getPrecioDeCompra();
                double precioCompra = jugadorEntra.getValorMercadoActual();
                double diferencia = precioVenta - precioCompra; // Positivo = ganó créditos

                double nuevoPresupuesto = equipo.getPresupuestoActual() + diferencia;
                if (nuevoPresupuesto < 0) {
                        throw new PresupuestoInsuficienteException(
                                        equipo.getPresupuestoActual(), precioCompra);
                }

                // ── 8. Ejecutar la transferencia ─────────────────────────────────────
                String nombreSale = slotSaliente.getJugadorReal().getNombreCompleto();
                String nombreEntra = jugadorEntra.getNombreCompleto();

                // 1ro: Hacemos el cambio temporal en la memoria
                slotSaliente.setJugadorReal(jugadorEntra);
                slotSaliente.setRol(request.getRolEntrante());
                slotSaliente.setPrecioDeCompra(precioCompra); // Congelar nuevo precio

                // 2do: AHORA validamos que la nueva formación no rompa la táctica elegida
                List<PosicionJugador> posicionesTitulares = plantel.getJugadores().stream()
                                .filter(jp -> jp.getRol() == RolPlantel.TITULAR || jp.getRol() == RolPlantel.CAPITAN)
                                .map(jp -> jp.getJugadorReal().getPosicion())
                                .toList();

                if (!FormacionValidator.esValida(plantel.getFormacion(), posicionesTitulares)) {
                        throw new FormacionInvalidaException(plantel.getFormacion());
                }

                // Si pasó la validación, persistimos en la base de datos
                jugadorPlantelRepo.save(slotSaliente);

                // ── 9. Actualizar contadores ─────────────────────────────────────────
                plantel.setTransferenciasUsadas(plantel.getTransferenciasUsadas() + 1);
                plantelRepo.save(plantel);

                equipo.setPresupuestoActual(nuevoPresupuesto);
                equipoVirtualRepo.save(equipo);

                log.info("[TRANSFERENCIA] Usuario {} | {} → {} | Diferencia: {} | " +
                                "Transferencias usadas: {}/3",
                                usuarioId, nombreSale, nombreEntra,
                                String.format("%.2f", diferencia),
                                plantel.getTransferenciasUsadas());

                return TransferenciaResultadoDto.builder()
                                .jugadorSaleNombre(nombreSale)
                                .jugadorEntraNombre(nombreEntra)
                                .diferenciaPresupuesto(Math.round(diferencia * 100.0) / 100.0)
                                .presupuestoRestante(Math.round(nuevoPresupuesto * 100.0) / 100.0)
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .build();
        }

        /**
         * Cambia el DT del plantel activo.
         * Consume 1 transferencia, igual que cambiar un jugador de campo.
         */
        @Transactional
        public TransferenciaResultadoDto cambiarDt(Long usuarioId, Long nuevoDtId) {

                // ── 1. Verificar jornada abierta ─────────────────────────────────────
                Jornada jornada = jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .orElseThrow(JornadaEnJuegoException::new);

                // ── 2. Cargar plantel ────────────────────────────────────────────────
                PlantelJornada plantel = plantelRepo
                                .findByUsuario_IdAndJornada_Id(usuarioId, jornada.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No tenés plantel armado para la jornada activa."));

                // ── 3. Verificar transferencias ──────────────────────────────────────
                if (!plantel.puedeHacerTransferencia()) {
                        throw new TransferenciasAgotadasException();
                }

                // ── 4. Cargar nuevo DT ───────────────────────────────────────────────
                JugadorReal nuevoDt = jugadorRepo.findById(nuevoDtId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "DT no encontrado: " + nuevoDtId));

                String nombreDtSale = plantel.getDt() != null
                                ? plantel.getDt().getNombreCompleto()
                                : "Sin DT";
                String nombreDtEntra = nuevoDt.getNombreCompleto();

                // ── 5. El DT no tiene precio de mercado — no afecta presupuesto ──────
                // Solo consume la transferencia
                plantel.setDt(nuevoDt);
                plantel.setTransferenciasUsadas(plantel.getTransferenciasUsadas() + 1);
                plantelRepo.save(plantel);

                double presupuestoActual = obtenerPresupuesto(usuarioId);

                log.info("[TRANSFERENCIA-DT] Usuario {} | {} → {} | Transferencias usadas: {}/3",
                                usuarioId, nombreDtSale, nombreDtEntra,
                                plantel.getTransferenciasUsadas());

                return TransferenciaResultadoDto.builder()
                                .jugadorSaleNombre(nombreDtSale)
                                .jugadorEntraNombre(nombreDtEntra)
                                .diferenciaPresupuesto(0.0) // DT no tiene precio
                                .presupuestoRestante(presupuestoActual)
                                .transferenciasUsadas(plantel.getTransferenciasUsadas())
                                .transferenciasRestantes(plantel.transferenciasRestantes())
                                .build();
        }
}