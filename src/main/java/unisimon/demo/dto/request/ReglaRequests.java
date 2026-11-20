package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpos de entrada de reglas de reserva. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReglaRequests {

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

    /** Aplica una regla existente a otra zona comun (crea el Rompimiento). */
    public record AsignarZona(@NotNull Long zonaComunId, @Size(max = 500) String observaciones) {
    }
}
