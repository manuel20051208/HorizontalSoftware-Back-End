package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.ReservaMapper;
import unisimon.demo.model.Residente;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.model.ZonaComun;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.ReservaZonaComunService;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

/**
 * Reservas de zonas comunes. Que el residente este habilitado y que la hora este dentro del horario son reglas
 * de {@link ReservaZonaComun}; la disponibilidad compara las reservas existentes con la capacidad de la zona.
 */
@Service
public class ReservaZonaComunServiceImpl implements ReservaZonaComunService {

    private final ReservaZonaComunRepository reservas;
    private final ZonaComunRepository zonas;
    private final ResidenteRepository residentes;
    private final NotificacionService notificaciones;
    private final Clock reloj;

    public ReservaZonaComunServiceImpl(ReservaZonaComunRepository reservas, ZonaComunRepository zonas,
                                       ResidenteRepository residentes, NotificacionService notificaciones,
                                       Clock reloj) {
        this.reservas = reservas;
        this.zonas = zonas;
        this.residentes = residentes;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ReservaResponses.Reserva reservarZonaComun(Long residenteId, ReservaRequests.Reservar s) {
        ContextoResidenteView contexto = contexto(residenteId);
        ZonaView vistaZona = zona(s.zonaComunId());
        boolean zonaAsignada = zonas.listarActivasDeInmueble(contexto.getInmuebleId()).stream()
                .anyMatch(z -> z.getId().equals(vistaZona.getId()));
        if (!zonaAsignada) {
            throw new ProhibidoException("Su inmueble no tiene asignada la zona " + vistaZona.getNombreZonaComun()
                    + " o esta inactiva");
        }

        Residente dueno = Hidratador.residente(contexto);
        ZonaComun zona = Hidratador.zona(vistaZona);
        ReservaZonaComun reserva = new ReservaZonaComun(null, dueno, zona);
        if (!reserva.estaHabilitado(dueno)) {
            throw new ProhibidoException("No esta habilitado para reservar: verifique que su cuenta este activa y "
                    + "que no tenga la administracion en mora");
        }
        if (!reserva.validarHorario(s.hora())) {
            throw new ConflictoException("La hora " + s.hora() + " esta fuera del horario de la zona ("
                    + zona.getHoraInicio() + " - " + zona.getHoraFin() + ")");
        }
        if (vistaZona.getReservasActuales() >= vistaZona.getCapacidad()) {
            throw new ConflictoException("La zona " + zona.getNombreZonaComun() + " no tiene disponibilidad");
        }
        if (reservas.existePorResidenteYZona(residenteId, zona.getId())) {
            throw new ConflictoException("Ya tiene una reserva de " + zona.getNombreZonaComun());
        }
        Long id = reservas.insertar(reserva);
        notificaciones.notificarReserva(id);
        return respuesta(id);
    }

    @Override
    @Transactional
    public void cancelarReservaZonaComun(Long residenteId, Long reservaId) {
        ReservaView reserva = reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reserva " + reservaId + " no existe"));
        if (!reserva.getDuenoReservaId().equals(residenteId)) {
            throw new ProhibidoException("Solo quien hizo la reserva puede cancelarla");
        }
        reservas.eliminar(reservaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponses.Reserva> verReservas(Long residenteId) {
        return reservas.listarPorResidente(residenteId).stream().map(this::mapear).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponses.Disponibilidad verificarDisponibilidad(Long residenteId, Long zonaComunId, LocalTime hora) {
        ContextoResidenteView contexto = contexto(residenteId);
        ZonaView vistaZona = zona(zonaComunId);
        Residente dueno = Hidratador.residente(contexto);
        ZonaComun zona = Hidratador.zona(vistaZona);
        ReservaZonaComun reserva = new ReservaZonaComun(null, dueno, zona);

        boolean horarioValido = reserva.validarHorario(hora);
        boolean habilitado = reserva.estaHabilitado(dueno);
        boolean hayCupo = vistaZona.getReservasActuales() < vistaZona.getCapacidad();
        boolean disponible = Boolean.TRUE.equals(zona.getIsActive()) && horarioValido && hayCupo;
        String mensaje = !Boolean.TRUE.equals(zona.getIsActive()) ? "La zona esta inactiva"
                : !horarioValido ? "La hora esta fuera del horario de la zona"
                : !hayCupo ? "La zona no tiene cupo"
                : !habilitado ? "No esta habilitado para reservar (cuenta inactiva o administracion en mora)"
                : "Disponible";
        return new ReservaResponses.Disponibilidad(zonaComunId, hora, horarioValido, disponible, habilitado, mensaje);
    }

    private ReservaResponses.Reserva respuesta(Long id) {
        return mapear(reservas.buscarPorId(id).orElseThrow());
    }

    private ReservaResponses.Reserva mapear(ReservaView vista) {
        ZonaComun zona = Hidratador.referenciaZona(vista.getZonaComunId());
        zona.setHoraInicio(vista.getHoraInicio());
        zona.setHoraFin(vista.getHoraFin());
        zona.setIsActive(vista.getZonaActiva());
        boolean enCurso = Boolean.TRUE.equals(zona.getIsActive()) && zona.horarioIncluye(LocalTime.now(reloj));
        return ReservaMapper.de(vista, enCurso);
    }

    private ContextoResidenteView contexto(Long residenteId) {
        return residentes.buscarContexto(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"));
    }

    private ZonaView zona(Long zonaId) {
        return zonas.buscarPorId(zonaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La zona comun " + zonaId + " no existe"));
    }
}
