package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Cuerpos de entrada de reservas. */
public final class ReservaRequests {

    private ReservaRequests() {
    }

    /** hora (HH:mm) en la que se quiere usar la zona: debe caer dentro del horario de la zona. */
    public record Reservar(@NotNull Long zonaComunId, @NotNull LocalTime hora) {
    }
}
