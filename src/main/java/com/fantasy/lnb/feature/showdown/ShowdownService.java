package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import com.fantasy.lnb.feature.showdown.dto.ShowdownEventoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownParticipanteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowdownService {

    private final ShowdownEventoRepository eventoRepo;
    private final ShowdownParticipanteRepository participanteRepo;
    private final JugadorRealRepository jugadorRepo;
    private final com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository estadisticaRepo;
    private final com.fantasy.lnb.feature.jornada.PartidoRepository partidoRepo;

    @Transactional(readOnly = true)
    public ShowdownEventoDto getEvento(String codigo) {
        ShowdownEvento evento = eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        
        EquipoReal local = evento.getPartido().getEquipoLocal();
        EquipoReal visitante = evento.getPartido().getEquipoVisitante();

        return ShowdownEventoDto.builder()
                .id(evento.getId())
                .codigoInscripcion(evento.getCodigoInscripcion())
                .estado(evento.getEstado())
                .localSigla(local.getSigla())
                .localNombre(local.getNombre())
                .visitanteSigla(visitante.getSigla())
                .visitanteNombre(visitante.getNombre())
                .fecha(evento.getPartido().getFechaHora().toString())
                .build();
    }

    @Transactional(readOnly = true)
    public List<JugadorReal> getMercado(String codigo) {
        ShowdownEvento evento = eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        
        Long localId = evento.getPartido().getEquipoLocal().getId();
        Long visitanteId = evento.getPartido().getEquipoVisitante().getId();

        return jugadorRepo.findAll().stream()
                .filter(j -> j.getEquipoReal() != null && 
                             (j.getEquipoReal().getId().equals(localId) || j.getEquipoReal().getId().equals(visitanteId)))
                .collect(Collectors.toList());
    }

    @Transactional
    public Long participar(String codigo, ParticiparShowdownRequest request) {
        ShowdownEvento evento = eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

        if (evento.getEstado() != EstadoShowdown.ABIERTO) {
            throw new IllegalStateException("El evento ya no acepta inscripciones");
        }

        // Recuperar jugadores
        JugadorReal base = jugadorRepo.findById(request.getBaseId()).orElseThrow();
        JugadorReal escolta = jugadorRepo.findById(request.getEscoltaId()).orElseThrow();
        JugadorReal alero = jugadorRepo.findById(request.getAleroId()).orElseThrow();
        JugadorReal alapivot = jugadorRepo.findById(request.getAlapivotId()).orElseThrow();
        JugadorReal pivot = jugadorRepo.findById(request.getPivotId()).orElseThrow();

        // Validar presupuesto (50cr máximo)
        double totalPrecio = base.getValorMercadoActual() + escolta.getValorMercadoActual() + 
                             alero.getValorMercadoActual() + alapivot.getValorMercadoActual() + pivot.getValorMercadoActual();
        if (totalPrecio > 50.0) {
            throw new IllegalArgumentException("El presupuesto excede los 50cr");
        }

        // Validar capitan
        List<Long> quintetoIds = List.of(base.getId(), escolta.getId(), alero.getId(), alapivot.getId(), pivot.getId());
        if (!quintetoIds.contains(request.getCapitanId())) {
            throw new IllegalArgumentException("El capitan debe ser uno de los 5 jugadores elegidos");
        }

        // Buscar si el usuario ya participó desde este dispositivo, si es así, se actualiza
        ShowdownParticipante participante = participanteRepo
                .findByEventoIdAndUuidDispositivo(evento.getId(), request.getUuidDispositivo())
                .orElseGet(() -> ShowdownParticipante.builder()
                        .evento(evento)
                        .uuidDispositivo(request.getUuidDispositivo())
                        .build());

        participante.setNombre(request.getNombre());
        participante.setApellido(request.getApellido());
        participante.setEmail(request.getEmail());
        participante.setCapitanId(request.getCapitanId());
        participante.setBase(base);
        participante.setEscolta(escolta);
        participante.setAlero(alero);
        participante.setAlaPivot(alapivot);
        participante.setPivot(pivot);

        participante = participanteRepo.save(participante);
        return participante.getId();
    }

    @Transactional(readOnly = true)
    public List<ShowdownParticipanteDto> getRanking(String codigo, String uuidDispositivo) {
        ShowdownEvento evento = eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

        return participanteRepo.findAllByEventoIdOrderByPuntosTotalesDesc(evento.getId())
                .stream()
                .map(p -> ShowdownParticipanteDto.builder()
                        .id(p.getId())
                        .nombre(p.getNombre())
                        .apellido(p.getApellido())
                        .puntosTotales(p.getPuntosTotales())
                        .esMio(p.getUuidDispositivo().equals(uuidDispositivo))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.fantasy.lnb.feature.showdown.dto.ShowdownMiEquipoDto getMiEquipo(String codigo, String uuidDispositivo) {
        ShowdownEvento evento = eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
                
        ShowdownParticipante participante = participanteRepo.findByEventoIdAndUuidDispositivo(evento.getId(), uuidDispositivo)
                .orElseThrow(() -> new IllegalArgumentException("No estás participando en este evento"));
                
        List<com.fantasy.lnb.feature.estadisticas.EstadisticaPartido> estadisticas = 
                estadisticaRepo.findByGesPartidoId(evento.getPartido().getGesHash());
                
        java.util.Map<Long, com.fantasy.lnb.feature.estadisticas.EstadisticaPartido> statsMap = estadisticas.stream()
                .collect(Collectors.toMap(e -> e.getJugadorReal().getId(), e -> e, (e1, e2) -> e1));
                
        List<JugadorReal> equipo = List.of(
                participante.getBase(), 
                participante.getEscolta(), 
                participante.getAlero(), 
                participante.getAlaPivot(), 
                participante.getPivot()
        );
        
        List<com.fantasy.lnb.feature.showdown.dto.ShowdownJugadorStatsDto> jugadoresStats = equipo.stream().map(j -> {
            boolean esCapitan = j.getId().equals(participante.getCapitanId());
            com.fantasy.lnb.feature.estadisticas.EstadisticaPartido stat = statsMap.get(j.getId());
            
            Double valFantasy = stat != null ? stat.getPuntajeFantasyCalculado() : 0.0;
            Double puntosAportados = esCapitan ? valFantasy * 1.5 : valFantasy;
            
            return com.fantasy.lnb.feature.showdown.dto.ShowdownJugadorStatsDto.builder()
                    .id(j.getId())
                    .nombreCompleto(j.getNombreCompleto())
                    .equipoSigla(j.getEquipoReal() != null ? j.getEquipoReal().getSigla() : "")
                    .equipoColorPrincipal(j.getEquipoReal() != null ? j.getEquipoReal().getColorPrincipal() : "")
                    .equipoColorSecundario(j.getEquipoReal() != null ? j.getEquipoReal().getColorSecundario() : "")
                    .equipoModeloCamiseta(j.getEquipoReal() != null ? j.getEquipoReal().getModeloCamiseta() : 1)
                    .numeroCamiseta(j.getNumeroCamiseta())
                    .posicion(j.getPosicion().name())
                    .esCapitan(esCapitan)
                    .fueTitular(stat != null ? stat.getFueTitularEnPartidoReal() : false)
                    .gano(stat != null ? stat.getEquipoGano() : false)
                    .puntos(stat != null ? stat.getPuntos() : 0)
                    .asistencias(stat != null ? stat.getAsistencias() : 0)
                    .rebotesDefensivos(stat != null ? stat.getRebotesDefensivos() : 0)
                    .rebotesOfensivos(stat != null ? stat.getRebotesOfensivos() : 0)
                    .recuperaciones(stat != null ? stat.getRecuperaciones() : 0)
                    .taponesRealizados(stat != null ? stat.getTaponesRealizados() : 0)
                    .faltasRecibidas(stat != null ? stat.getFaltasRecibidas() : 0)
                    .perdidas(stat != null ? stat.getPerdidas() : 0)
                    .taponesRecibidos(stat != null ? stat.getTaponesRecibidos() : 0)
                    .faltasCometidas(stat != null ? stat.getFaltasCometidas() : 0)
                    .tirosDeCampoFallados(stat != null ? stat.getTirosCampoFallados() : 0)
                    .tirosLibresFallados(stat != null ? stat.getTirosLibresFallados() : 0)
                    .valFantasy(valFantasy)
                    .puntosAportados(puntosAportados)
                    .build();
        }).collect(Collectors.toList());
        
        return com.fantasy.lnb.feature.showdown.dto.ShowdownMiEquipoDto.builder()
                .participanteId(participante.getId())
                .puntosTotales(participante.getPuntosTotales())
                .jugadores(jugadoresStats)
                .build();
    }
    
    // --- Admin Methods ---

    public List<com.fantasy.lnb.feature.showdown.dto.AdminShowdownDto> getTodosLosShowdowns() {
        return eventoRepo.findAll().stream().map(evento -> {
            EquipoReal local = evento.getPartido().getEquipoLocal();
            EquipoReal visitante = evento.getPartido().getEquipoVisitante();
            int count = participanteRepo.countByEventoId(evento.getId());

            return com.fantasy.lnb.feature.showdown.dto.AdminShowdownDto.builder()
                    .id(evento.getId())
                    .codigoInscripcion(evento.getCodigoInscripcion())
                    .estado(evento.getEstado())
                    .localSigla(local.getSigla())
                    .localNombre(local.getNombre())
                    .visitanteSigla(visitante.getSigla())
                    .visitanteNombre(visitante.getNombre())
                    .fecha(evento.getPartido().getFechaHora().toString())
                    .participantesCount(count)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<com.fantasy.lnb.feature.jornada.dto.PartidoDto> getPartidosDisponiblesParaShowdown() {
        List<com.fantasy.lnb.feature.jornada.Partido> partidosProgramados = partidoRepo.findByEstado(com.fantasy.lnb.feature.jornada.EstadoPartido.PROGRAMADO);
        
        // Excluir los que ya tienen showdown
        List<Long> partidosConShowdown = eventoRepo.findAll().stream()
                .map(e -> e.getPartido().getId())
                .collect(Collectors.toList());

        return partidosProgramados.stream()
                .filter(p -> !partidosConShowdown.contains(p.getId()))
                .map(p -> com.fantasy.lnb.feature.jornada.dto.PartidoDto.builder()
                        .id(p.getId())
                        .equipoLocal(p.getEquipoLocal().getNombre())
                        .siglaLocal(p.getEquipoLocal().getSigla())
                        .equipoVisitante(p.getEquipoVisitante().getNombre())
                        .siglaVisitante(p.getEquipoVisitante().getSigla())
                        .fechaHora(p.getFechaHora())
                        .puntosLocal(p.getPuntosLocal())
                        .puntosVisitante(p.getPuntosVisitante())
                        .estado(p.getEstado().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public com.fantasy.lnb.feature.showdown.dto.AdminShowdownDto crearShowdownManual(Long partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido no encontrado"));

        String codigo = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        var evento = ShowdownEvento.builder()
                .partido(partido)
                .codigoInscripcion(codigo)
                .estado(EstadoShowdown.ABIERTO)
                .build();
        
        evento = eventoRepo.save(evento);

        EquipoReal local = evento.getPartido().getEquipoLocal();
        EquipoReal visitante = evento.getPartido().getEquipoVisitante();

        return com.fantasy.lnb.feature.showdown.dto.AdminShowdownDto.builder()
                .id(evento.getId())
                .codigoInscripcion(evento.getCodigoInscripcion())
                .estado(evento.getEstado())
                .localSigla(local.getSigla())
                .localNombre(local.getNombre())
                .visitanteSigla(visitante.getSigla())
                .visitanteNombre(visitante.getNombre())
                .fecha(evento.getPartido().getFechaHora().toString())
                .participantesCount(0)
                .build();
    }

    @Transactional
    public void eliminarShowdown(Long eventoId) {
        var evento = eventoRepo.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        eventoRepo.delete(evento);
    }
}
