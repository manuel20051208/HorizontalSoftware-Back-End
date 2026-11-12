package unisimon.demo.service;

import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;

import java.time.LocalDateTime;

/** Reservas de zonas comunes hechas por residentes. */
public interface ReservaZonaComunService {

    /**
     * Residente.reservarZonaComun(id): valida el intervalo, la habilitacion, el horario, las reglas de la zona,
     * el cupo y que no se cruce con otra reserva suya de la misma zona; luego registra la reserva.
     */
    ReservaResponses.Reserva reservarZonaComun(Long residenteId, ReservaRequests.Reservar solicitud);

    /** Residente.cancelarReservaZonaComun(id): solo el dueno, y solo mientras la reserva no haya empezado. */
    void cancelarReservaZonaComun(Long residenteId, Long reservaId);

    /** Residente.verReservas(id): sus reservas, opcionalmente solo las de un estado (PROGRAMADA, EN_CURSO, FINALIZADA). */
    Pagina<ReservaResponses.Reserva> verReservas(Long residenteId, String estado, SolicitudPagina pagina);

    /** Las mismas validaciones de reservar, sin reservar: informa que condiciones se cumplen. */
    ReservaResponses.Disponibilidad verificarDisponibilidad(Long residenteId, Long zonaComunId,
                                                            LocalDateTime inicio, LocalDateTime fin);

    /** Inmueble asignado al residente; sirve para listarle solo las zonas comunes de ese inmueble. */
    Long inmuebleDelResidente(Long residenteId);
}
