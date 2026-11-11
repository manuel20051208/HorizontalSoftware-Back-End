package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Cuerpos de entrada de reservas. */
public final class ReservaRequests {

    private ReservaRequests() {
    }

    /**
     * Intervalo en el que se quiere usar la zona, en hora de Colombia ({@code yyyy-MM-ddTHH:mm}).
     * Debe ser futuro, con el fin despues del inicio, dentro del horario de la zona y cumplir sus reglas de reserva.
     */
    public record Reservar(@NotNull Long zonaComunId,
                           @NotNull LocalDateTime fechaHoraInicio,
                           @NotNull LocalDateTime fechaHoraFin) {
    }
}
