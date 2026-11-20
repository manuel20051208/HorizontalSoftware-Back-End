package unisimon.demo.service;

import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.ZonaResponses;

import java.time.LocalDateTime;

/** Reservas de zonas comunes hechas por residentes. */
public interface ReservaZonaComunService {

    /**
     * Residente.reservarZonaComun(inmueble, id): valida el intervalo, la habilitacion, el horario, las reglas de la
     * zona, el cupo y que no se cruce con otra reserva suya de la misma zona; luego registra la reserva en el
     * inmueble elegido con {@code inmuebleId}.
     */
    ReservaResponses.Reserva reservarZonaComun(Long residenteId, Long inmuebleId,
                                              ReservaRequests.Reservar solicitud);

    /** Residente.cancelarReservaZonaComun(id): solo el dueno, y solo mientras la reserva no haya empezado. */
    void cancelarReservaZonaComun(Long residenteId, Long reservaId);

    /**
     * Residente.verReservas(id): sus reservas del inmueble elegido por {@code inmuebleId} (nulo: el unico suyo),
     * opcionalmente solo las de un estado (PROGRAMADA, EN_CURSO, FINALIZADA).
     */
    Pagina<ReservaResponses.Reserva> verReservas(Long residenteId, Long inmuebleId, String estado,
                                                 SolicitudPagina pagina);

    /** Las mismas validaciones de reservar, sin reservar: informa que condiciones se cumplen. */
    ReservaResponses.Disponibilidad verificarDisponibilidad(Long residenteId, Long inmuebleId, Long zonaComunId,
                                                            LocalDateTime inicio, LocalDateTime fin);

    /** Residente.mostrarZonasComunes(): las zonas comunes activas del inmueble elegido, para reservar. */
    Pagina<ZonaResponses.Zona> zonasDisponibles(Long residenteId, Long inmuebleId, SolicitudPagina pagina);

    /** Una reserva propia. */
    ReservaResponses.Reserva consultarReserva(Long residenteId, Long reservaId);

    /** Cambia el intervalo de una reserva propia que aun no empieza, con las mismas validaciones de reservar. */
    ReservaResponses.Reserva reprogramarReserva(Long residenteId, Long reservaId, ReservaRequests.Reprogramar solicitud);
}
