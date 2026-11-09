package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpos de entrada de reglas de reserva. */
public final class ReglaRequests {

    private ReglaRequests() {
    }

    public record CrearRegla(@NotNull @PositiveOrZero Integer anticipacionMinimaHoras,
                             @NotNull @Positive Integer duracionMaximaHoras,
                             @PositiveOrZero Integer horasAntelacionPenalidad,
                             @PositiveOrZero BigDecimal tarifaPenalidad,
                             @NotEmpty List<@NotNull Long> zonaComunIds,
                             @Size(max = 500) String observaciones) {
    }

    public record ActualizarReglas(@NotNull @PositiveOrZero Integer anticipacionMinimaHoras,
                                   @NotNull @Positive Integer duracionMaximaHoras) {
    }
}
