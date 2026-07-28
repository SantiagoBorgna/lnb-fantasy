package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.exception.TransferenciasAgotadasException;
import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.dt.DirectorTecnicoRepository;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.dto.JugadorMercadoDto;
import com.fantasy.lnb.feature.mercado.dto.PropuestaTraspasoDto;
import com.fantasy.lnb.feature.mercado.dto.PropuestaTraspasoRequest;
import com.fantasy.lnb.feature.plantel.*;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TraspasoUsuarioService {

    private final PropuestaTraspasoRepository propuestaRepo;
    private final TorneoRepository torneoRepo;
    private final JornadaRepository jornadaRepo;
    private final EquipoVirtualRepository equipoRepo;
    private final JugadorRealRepository jugadorRepo;
    private final DirectorTecnicoRepository dtRepo;
    private final PlantelJornadaRepository plantelRepo;
    private final JugadorPlantelRepository jugadorPlantelRepo;
    private final TransaccionDraftRepository transaccionDraftRepo;
    private final PushNotificationService pushService;
    
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private PlantelService plantelService;
    
    // Convertir entidad a Dto
    private PropuestaTraspasoDto mapToDto(PropuestaTraspaso p) {
        return PropuestaTraspasoDto.builder()
                .id(p.getId())
                .torneoId(p.getTorneo().getId())
                .equipoProponenteId(p.getEquipoProponente().getId())
                .equipoProponenteNombre(p.getEquipoProponente().getNombre())
                .equipoProponenteUsuarioNombre(p.getEquipoProponente().getUsuario().getNombreDisplay())
                .equipoReceptorId(p.getEquipoReceptor().getId())
                .equipoReceptorNombre(p.getEquipoReceptor().getNombre())
                .equipoReceptorUsuarioNombre(p.getEquipoReceptor().getUsuario().getNombreDisplay())
                .jugadoresOfrecidos(p.getJugadoresOfrecidos().stream().map(j -> JugadorMercadoDto.builder()
                        .id(j.getId())
                        .nombreCompleto(j.getNombreCompleto())
                        .posicion(j.getPosicion())
                        .equipoNombre(j.getEquipoReal() != null ? j.getEquipoReal().getNombre() : null)
                        .valorMercadoActual(j.getValorMercadoActual())
                        .build()).collect(Collectors.toList()))
                .jugadoresSolicitados(p.getJugadoresSolicitados().stream().map(j -> JugadorMercadoDto.builder()
                        .id(j.getId())
                        .nombreCompleto(j.getNombreCompleto())
                        .posicion(j.getPosicion())
                        .equipoNombre(j.getEquipoReal() != null ? j.getEquipoReal().getNombre() : null)
                        .valorMercadoActual(j.getValorMercadoActual())
                        .build()).collect(Collectors.toList()))
                .dtOfrecidoId(p.getDtOfrecido() != null ? p.getDtOfrecido().getId() : null)
                .dtOfrecidoNombre(p.getDtOfrecido() != null ? p.getDtOfrecido().getNombreCompleto() : null)
                .dtSolicitadoId(p.getDtSolicitado() != null ? p.getDtSolicitado().getId() : null)
                .dtSolicitadoNombre(p.getDtSolicitado() != null ? p.getDtSolicitado().getNombreCompleto() : null)
                .estado(p.getEstado().name())
                .fechaCreacion(p.getFechaCreacion())
                .fechaResolucion(p.getFechaResolucion())
                .build();
    }

    public org.springframework.data.domain.Page<PropuestaTraspasoDto> obtenerMisPropuestas(Long torneoId, Long usuarioId, org.springframework.data.domain.Pageable pageable) {
        return propuestaRepo.findByTorneo_IdAndEquipoProponente_Usuario_IdOrTorneo_IdAndEquipoReceptor_Usuario_IdOrderByFechaCreacionDesc(
                torneoId, usuarioId, torneoId, usuarioId, pageable)
                .map(this::mapToDto);
    }

    @Transactional
    public PropuestaTraspasoDto proponerTraspaso(Long usuarioId, PropuestaTraspasoRequest request) {
        Jornada jornada = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElseThrow(() -> new IllegalStateException("El mercado no está abierto a cambios."));
                
        if (WaiverService.esFaseRestringida(jornada)) {
            throw new IllegalStateException("No se pueden proponer traspasos durante la Agencia Restringida, espera que abra la Agencia Libre.");
        }

        Torneo torneo = torneoRepo.findById(request.getTorneoId())
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado."));

        EquipoVirtual equipoProponente = equipoRepo.findByUsuario_Id(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Equipo virtual no encontrado para el usuario"));

        EquipoVirtual equipoReceptor = equipoRepo.findById(request.getEquipoReceptorId())
                .orElseThrow(() -> new IllegalArgumentException("Equipo receptor no encontrado."));

        if (equipoProponente.getId().equals(equipoReceptor.getId())) {
            throw new IllegalArgumentException("No puedes proponerte un traspaso a ti mismo.");
        }

        List<JugadorReal> ofrecidos = new ArrayList<>();
        if (request.getJugadoresOfrecidosIds() != null && !request.getJugadoresOfrecidosIds().isEmpty()) {
            ofrecidos = jugadorRepo.findAllById(request.getJugadoresOfrecidosIds());
        }

        List<JugadorReal> solicitados = new ArrayList<>();
        if (request.getJugadoresSolicitadosIds() != null && !request.getJugadoresSolicitadosIds().isEmpty()) {
            solicitados = jugadorRepo.findAllById(request.getJugadoresSolicitadosIds());
        }

        DirectorTecnico dtOfrecido = null;
        if (request.getDtOfrecidoId() != null) {
            dtOfrecido = dtRepo.findById(request.getDtOfrecidoId()).orElse(null);
        }

        DirectorTecnico dtSolicitado = null;
        if (request.getDtSolicitadoId() != null) {
            dtSolicitado = dtRepo.findById(request.getDtSolicitadoId()).orElse(null);
        }

        int cantidadOfrecida = ofrecidos.size() + (dtOfrecido != null ? 1 : 0);
        int cantidadSolicitada = solicitados.size() + (dtSolicitado != null ? 1 : 0);

        if (cantidadOfrecida == 0 || cantidadSolicitada == 0) {
            throw new IllegalArgumentException("Debes incluir al menos un elemento de cada lado.");
        }

        if (cantidadOfrecida != cantidadSolicitada) {
            throw new IllegalArgumentException("Los traspasos deben ser equitativos en cantidad (ej: 1x1, 2x2).");
        }

        if ((dtOfrecido != null && dtSolicitado == null) || (dtOfrecido == null && dtSolicitado != null)) {
            throw new IllegalArgumentException("Si incluyes un Director Técnico en el traspaso, debes recibir un Director Técnico a cambio.");
        }

        // Simulamos la validación del plantel del proponente
        PlantelJornada plantelProponente = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                equipoProponente.getUsuario().getId(), jornada.getId(), torneo.getId())
                .orElseThrow(() -> new IllegalStateException("El proponente no tiene plantel armado."));

        plantelService.validarPlantelPostTraspaso(plantelProponente, ofrecidos, solicitados);

        PropuestaTraspaso propuesta = PropuestaTraspaso.builder()
                .torneo(torneo)
                .jornada(jornada)
                .equipoProponente(equipoProponente)
                .equipoReceptor(equipoReceptor)
                .jugadoresOfrecidos(ofrecidos)
                .jugadoresSolicitados(solicitados)
                .dtOfrecido(dtOfrecido)
                .dtSolicitado(dtSolicitado)
                .build();

        propuesta = propuestaRepo.save(propuesta);

        pushService.enviarNotificacionAUsuario(
                equipoReceptor.getUsuario(),
                "Propuesta de Traspaso",
                equipoProponente.getUsuario().getNombreDisplay() + " te ha propuesto un traspaso de jugadores.",
                "/mercado",
                "MERCADO_TRASPASO"
        );

        return mapToDto(propuesta);
    }

    @Transactional
    public void cancelarTraspaso(Long usuarioId, Long propuestaId) {
        PropuestaTraspaso p = propuestaRepo.findById(propuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada."));
        if (!p.getEquipoProponente().getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("Sólo el proponente puede cancelar la propuesta.");
        }
        if (p.getEstado() != EstadoPropuestaTraspaso.PENDIENTE) {
            throw new IllegalStateException("La propuesta ya no está disponible.");
        }
        p.setEstado(EstadoPropuestaTraspaso.CANCELADA_USUARIO);
        p.setFechaResolucion(LocalDateTime.now());
        propuestaRepo.save(p);
    }

    @Transactional
    public void rechazarTraspaso(Long usuarioId, Long propuestaId) {
        PropuestaTraspaso p = propuestaRepo.findById(propuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada."));
        if (!p.getEquipoReceptor().getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("Sólo el receptor puede rechazar la propuesta.");
        }
        if (p.getEstado() != EstadoPropuestaTraspaso.PENDIENTE) {
            throw new IllegalStateException("La propuesta ya no está disponible.");
        }
        p.setEstado(EstadoPropuestaTraspaso.RECHAZADA);
        p.setFechaResolucion(LocalDateTime.now());
        propuestaRepo.save(p);

        pushService.enviarNotificacionAUsuario(
                p.getEquipoProponente().getUsuario(),
                "Resolución de Traspaso",
                p.getEquipoReceptor().getUsuario().getNombreDisplay() + " rechazó tu propuesta de traspaso.",
                "/mercado",
                "MERCADO_TRASPASO"
        );
    }

    @Transactional
    public void aceptarTraspaso(Long usuarioId, Long propuestaId) {
        PropuestaTraspaso p = propuestaRepo.findById(propuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada."));
        if (!p.getEquipoReceptor().getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("Sólo el receptor puede aceptar la propuesta.");
        }
        if (p.getEstado() != EstadoPropuestaTraspaso.PENDIENTE) {
            throw new IllegalStateException("La propuesta ya no está disponible.");
        }

        Jornada jornada = jornadaRepo.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .orElseThrow(() -> new IllegalStateException("El mercado no está abierto a cambios."));
        if (WaiverService.esFaseRestringida(jornada)) {
            throw new IllegalStateException("No se pueden aceptar traspasos durante la Fase Restringida.");
        }

        PlantelJornada plantelProponente = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                p.getEquipoProponente().getUsuario().getId(), jornada.getId(), p.getTorneo().getId())
                .orElseThrow(() -> new IllegalStateException("El proponente no tiene plantel armado."));

        PlantelJornada plantelReceptor = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                p.getEquipoReceptor().getUsuario().getId(), jornada.getId(), p.getTorneo().getId())
                .orElseThrow(() -> new IllegalStateException("No tienes plantel armado."));

        int costo = p.getJugadoresOfrecidos().size() + (p.getDtOfrecido() != null ? 1 : 0);

        if (plantelProponente.transferenciasRestantes() < costo) {
            throw new TransferenciasAgotadasException(4);
        }
        if (plantelReceptor.transferenciasRestantes() < costo) {
            throw new TransferenciasAgotadasException(4);
        }

        // Validar ambos planteles post-traspaso antes de ejecutar
        plantelService.validarPlantelPostTraspaso(plantelProponente, p.getJugadoresOfrecidos(), p.getJugadoresSolicitados());
        plantelService.validarPlantelPostTraspaso(plantelReceptor, p.getJugadoresSolicitados(), p.getJugadoresOfrecidos());

        // Ejecutar intercambios de jugadores
        for (JugadorReal ofrecido : p.getJugadoresOfrecidos()) {
            JugadorPlantel jpOfrecido = plantelProponente.getJugadores().stream()
                    .filter(jp -> jp.getJugadorReal().getId().equals(ofrecido.getId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("El proponente ya no tiene a " + ofrecido.getNombreCompleto()));
            
            // Buscar con qué jugador solicitado intercambiar
            // Asumiremos que el intercambio es posicional o validaremos luego
            // Por simplicidad, intercambiaremos los slots directamente asumiendo misma posición y cantidad
            // Como las listas tienen el mismo tamaño, usamos zip logic:
            int index = p.getJugadoresOfrecidos().indexOf(ofrecido);
            JugadorReal solicitado = p.getJugadoresSolicitados().get(index);
            
            JugadorPlantel jpSolicitado = plantelReceptor.getJugadores().stream()
                    .filter(jp -> jp.getJugadorReal().getId().equals(solicitado.getId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("El receptor ya no tiene a " + solicitado.getNombreCompleto()));

            // Swap
            jpOfrecido.setJugadorReal(solicitado);
            jpSolicitado.setJugadorReal(ofrecido);
            
            jugadorPlantelRepo.save(jpOfrecido);
            jugadorPlantelRepo.save(jpSolicitado);

            // Transacciones draft
            transaccionDraftRepo.save(com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                    .torneo(p.getTorneo())
                    .usuario(p.getEquipoProponente().getUsuario())
                    .jornada(jornada)
                    .jugadorEntra(solicitado)
                    .jugadorSale(ofrecido)
                    .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.TRASPASO)
                    .build());
            
            transaccionDraftRepo.save(com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                    .torneo(p.getTorneo())
                    .usuario(p.getEquipoReceptor().getUsuario())
                    .jornada(jornada)
                    .jugadorEntra(ofrecido)
                    .jugadorSale(solicitado)
                    .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.TRASPASO)
                    .build());
        }

        // Intercambiar DTs
        if (p.getDtOfrecido() != null && p.getDtSolicitado() != null) {
            DirectorTecnico dtOfrecido = p.getDtOfrecido();
            DirectorTecnico dtSolicitado = p.getDtSolicitado();
            
            if (plantelProponente.getDt() == null || !plantelProponente.getDt().getId().equals(dtOfrecido.getId())) {
                throw new IllegalStateException("El proponente ya no tiene al DT " + dtOfrecido.getNombreCompleto());
            }
            if (plantelReceptor.getDt() == null || !plantelReceptor.getDt().getId().equals(dtSolicitado.getId())) {
                throw new IllegalStateException("El receptor ya no tiene al DT " + dtSolicitado.getNombreCompleto());
            }

            plantelProponente.setDt(dtSolicitado);
            plantelReceptor.setDt(dtOfrecido);

            transaccionDraftRepo.save(com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                    .torneo(p.getTorneo())
                    .usuario(p.getEquipoProponente().getUsuario())
                    .jornada(jornada)
                    .dtEntra(dtSolicitado)
                    .dtSale(dtOfrecido)
                    .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.TRASPASO)
                    .build());
            
            transaccionDraftRepo.save(com.fantasy.lnb.feature.mercado.TransaccionDraft.builder()
                    .torneo(p.getTorneo())
                    .usuario(p.getEquipoReceptor().getUsuario())
                    .jornada(jornada)
                    .dtEntra(dtOfrecido)
                    .dtSale(dtSolicitado)
                    .tipo(com.fantasy.lnb.feature.mercado.TipoTransaccionDraft.TRASPASO)
                    .build());
        }

        plantelProponente.setTransferenciasUsadas(plantelProponente.getTransferenciasUsadas() + costo);
        plantelReceptor.setTransferenciasUsadas(plantelReceptor.getTransferenciasUsadas() + costo);
        plantelRepo.save(plantelProponente);
        plantelRepo.save(plantelReceptor);

        p.setEstado(EstadoPropuestaTraspaso.ACEPTADA);
        p.setFechaResolucion(LocalDateTime.now());
        propuestaRepo.save(p);

        // Cancelar propuestas salientes que ya no se pueden cubrir con las transferencias restantes
        cancelarPropuestasInviables(p.getEquipoProponente().getUsuario().getId(), p.getTorneo().getId(), plantelProponente);
        cancelarPropuestasInviables(p.getEquipoReceptor().getUsuario().getId(), p.getTorneo().getId(), plantelReceptor);

        pushService.enviarNotificacionAUsuario(
                p.getEquipoProponente().getUsuario(),
                "Resolución de Traspaso",
                p.getEquipoReceptor().getUsuario().getNombreDisplay() + " aceptó tu propuesta de traspaso.",
                "/mercado",
                "MERCADO_TRASPASO"
        );
    }

    private void cancelarPropuestasInviables(Long usuarioId, Long torneoId, PlantelJornada plantel) {
        List<PropuestaTraspaso> pendientes = propuestaRepo.findPendientesSalientes(usuarioId, torneoId);
        for (PropuestaTraspaso pendiente : pendientes) {
            int costo = pendiente.getJugadoresOfrecidos().size() + (pendiente.getDtOfrecido() != null ? 1 : 0);
            if (plantel.transferenciasRestantes() < costo) {
                pendiente.setEstado(EstadoPropuestaTraspaso.CANCELADA_SISTEMA);
                pendiente.setFechaResolucion(LocalDateTime.now());
                propuestaRepo.save(pendiente);
            }
        }
    }

    @Transactional
    public void cancelarPropuestasPendientes() {
        List<PropuestaTraspaso> pendientes = propuestaRepo.findByEstado(EstadoPropuestaTraspaso.PENDIENTE);
        pendientes.forEach(p -> {
            p.setEstado(EstadoPropuestaTraspaso.CANCELADA_SISTEMA);
            p.setFechaResolucion(LocalDateTime.now());
        });
        propuestaRepo.saveAll(pendientes);
    }

    @Transactional
    public void cancelarPropuestasConJugador(Long jugadorId) {
        List<PropuestaTraspaso> list1 = propuestaRepo.findPendientesByJugadorOfrecido(jugadorId);
        List<PropuestaTraspaso> list2 = propuestaRepo.findPendientesByJugadorSolicitado(jugadorId);
        List<PropuestaTraspaso> all = new ArrayList<>();
        all.addAll(list1);
        all.addAll(list2);
        all.forEach(p -> {
            p.setEstado(EstadoPropuestaTraspaso.CANCELADA_SISTEMA);
            p.setFechaResolucion(LocalDateTime.now());
        });
        propuestaRepo.saveAll(all);
    }

    @Transactional
    public void cancelarPropuestasConDt(Long dtId) {
        List<PropuestaTraspaso> pendientes = propuestaRepo.findPendientesByDt(dtId);
        pendientes.forEach(p -> {
            p.setEstado(EstadoPropuestaTraspaso.CANCELADA_SISTEMA);
            p.setFechaResolucion(LocalDateTime.now());
        });
        propuestaRepo.saveAll(pendientes);
    }
}
