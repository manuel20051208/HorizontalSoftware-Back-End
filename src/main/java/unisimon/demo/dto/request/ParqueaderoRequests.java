package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Cuerpos de entrada de parqueaderos (Paqueadero en el diagrama). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParqueaderoRequests {

    /** Un inmueble tiene a lo sumo un parqueadero (Inmueble ◆— Paqueadero, 0..1). */
    public record CrearParqueadero(@NotNull @Positive Long inmuebleId,
                                   @NotNull @PositiveOrZero BigDecimal valorMensual) {
    }

    public record ActualizarParqueadero(@NotNull @PositiveOrZero BigDecimal valorMensual) {
    }
}
