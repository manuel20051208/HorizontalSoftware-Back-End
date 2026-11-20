package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Cuerpos de entrada de autos. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AutoRequests {

    /** placa es numerica porque asi esta en el diagrama. parqueaderoId es opcional. */
    public record RegistrarAuto(@NotNull @Positive Long placa, Long parqueaderoId) {
    }

    /** parqueaderoId nulo deja el auto sin parqueadero. */
    public record ActualizarAuto(@NotNull @Positive Long placa, Long parqueaderoId) {
    }
}
