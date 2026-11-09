package unisimon.demo.dto.response;

import java.time.LocalTime;

/** Respuestas sobre reservas de zonas comunes. */
public final class ReservaResponses {

    private ReservaResponses() {
    }

    /** enCurso: la zona esta abierta ahora mismo (dentro de su horario) y activa. */
    public record Reserva(Long id, Long zonaComunId, String nombreZonaComun, LocalTime horaInicio, LocalTime horaFin,
                          Boolean enCurso, Long duenoReservaId, String nombreDueno, Long inmuebleId,
                          String direccionInmueble) {
    }

    public record Disponibilidad(Long zonaComunId, LocalTime hora, Boolean horarioValido, Boolean disponible,
                                 Boolean habilitado, String mensaje) {
    }
}
