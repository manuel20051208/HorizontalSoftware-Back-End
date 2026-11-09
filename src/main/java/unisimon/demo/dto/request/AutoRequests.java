package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Cuerpos de entrada de autos. */
public final class AutoRequests {

    private AutoRequests() {
    }

    /** placa es numerica porque asi esta en el diagrama. parqueaderoId es opcional. */
    public record RegistrarAuto(@NotNull @Positive Long placa, Long parqueaderoId) {
    }
}
