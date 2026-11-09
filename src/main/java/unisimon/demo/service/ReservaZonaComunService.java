package unisimon.demo.service;

import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.response.ReservaResponses;

import java.time.LocalTime;
import java.util.List;

/** Reservas de zonas comunes hechas por residentes. */
public interface ReservaZonaComunService {

    /** Residente.reservarZonaComun(id): valida habilitacion, horario y disponibilidad y registra la reserva. */
    ReservaResponses.Reserva reservarZonaComun(Long residenteId, ReservaRequests.Reservar solicitud);

    /** Residente.cancelarReservaZonaComun(id): solo el dueno de la reserva puede cancelarla. */
    void cancelarReservaZonaComun(Long residenteId, Long reservaId);

    /** Residente.verReservas(id) */
    List<ReservaResponses.Reserva> verReservas(Long residenteId);

    /** ReservaZonaComun.validarHorario + verificarDisponibilidad + estaHabilitado, sin reservar. */
    ReservaResponses.Disponibilidad verificarDisponibilidad(Long residenteId, Long zonaComunId, LocalTime hora);
}
