package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpos de entrada de inmuebles, tarifas y multas. */
public final class InmuebleRequests {

    private InmuebleRequests() {
    }

    /** direccionInmueble con formato TORRE-APTO, por ejemplo "A-302". valorParqueadero es opcional. */
    public record CrearInmueble(@NotBlank @Size(max = 100) String direccionInmueble,
                                @PositiveOrZero BigDecimal valorParqueadero) {
    }

    public record Tarifas(@NotNull @PositiveOrZero BigDecimal cuotaAdministracion,
                          @NotNull @PositiveOrZero BigDecimal valorServiciosComunes,
                          @PositiveOrZero BigDecimal valorParqueadero) {
    }

    public record Multas(@NotNull @PositiveOrZero BigDecimal valorMultas) {
    }

    public record AsignarZonas(@NotEmpty List<@NotNull Long> zonaComunIds) {
    }
}
