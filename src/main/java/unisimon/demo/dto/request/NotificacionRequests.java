package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Cuerpos de entrada de notificaciones. */
public final class NotificacionRequests {

    private NotificacionRequests() {
    }

    /**
     * Destinatarios: usuarioIds, o inmuebleId (sus residentes activos). Si no se envia ninguno, la reciben
     * todos los residentes activos. tipoNotificacion por defecto: GENERAL.
     */
    public record Enviar(@NotBlank @Size(max = 500) String mensaje,
                         @Size(max = 30) String tipoNotificacion,
                         List<Long> usuarioIds,
                         Long inmuebleId) {
    }
}
