package unisimon.demo.dto.response;

import java.time.LocalTime;

/** Respuestas sobre zonas comunes. */
public final class ZonaResponses {

    private ZonaResponses() {
    }

    public record Zona(Long id, String nombreZonaComun, Integer capacidad, LocalTime horaInicio, LocalTime horaFin,
                       Boolean isActive, Long reservasActuales) {
    }
}
