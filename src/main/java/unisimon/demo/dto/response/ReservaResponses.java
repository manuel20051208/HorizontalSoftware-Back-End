package unisimon.demo.dto.response;

import java.time.LocalDateTime;

/** Respuestas sobre reservas de zonas comunes. */
public final class ReservaResponses {

    private ReservaResponses() {
    }

    /**
     * estado: PROGRAMADA (aun no empieza), EN_CURSO o FINALIZADA, calculado con la hora actual de Colombia.
     * enCurso: la reserva esta EN_CURSO y la zona sigue activa (puede usarse en este momento).
     * cancelable: solo una reserva PROGRAMADA se puede cancelar.
     */
    public record Reserva(Long id, Long zonaComunId, String nombreZonaComun,
                          LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin, String estado,
                          Boolean enCurso, Boolean cancelable, Long duenoReservaId, String nombreDueno,
                          Long inmuebleId, String direccionInmueble) {
    }

    /**
     * disponible: se puede reservar ese intervalo ahora mismo (todas las condiciones se cumplen).
     * mensaje: "Disponible" o el primer motivo por el que no se puede.
     */
    public record Disponibilidad(Long zonaComunId, LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin,
                                 Boolean horarioValido, Boolean cumpleReglas, Boolean hayCupo, Boolean habilitado,
                                 Boolean disponible, String mensaje) {
    }
}
