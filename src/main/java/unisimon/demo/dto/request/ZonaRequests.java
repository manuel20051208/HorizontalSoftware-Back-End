package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Cuerpos de entrada de zonas comunes. */
public final class ZonaRequests {

    private ZonaRequests() {
    }

    /** aplicarATodosLosInmuebles (opcional, por defecto true): asigna la zona nueva a todos los inmuebles. */
    public record CrearZona(@NotBlank @Size(max = 100) String nombreZonaComun,
                            @NotNull @Positive Integer capacidad,
                            @NotNull LocalTime horaInicio,
                            @NotNull LocalTime horaFin,
                            Boolean aplicarATodosLosInmuebles) {
    }
}
