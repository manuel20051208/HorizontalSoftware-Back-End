package unisimon.demo.dto.response;

import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas sobre zonas comunes. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZonaResponses {

    public record Zona(Long id, String nombreZonaComun, Integer capacidad, LocalTime horaInicio, LocalTime horaFin,
                       Boolean isActive, Long reservasActuales) {
    }
}
