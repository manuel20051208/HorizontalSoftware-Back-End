package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/** Cuerpos de entrada de notificaciones. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificacionRequests {

    /**
     * Destinatarios: usuarioIds, o inmuebleId (sus residentes activos). Si no se envia ninguno, la reciben
     * todos los residentes activos. tipoNotificacion por defecto: GENERAL.
     */
    public record Enviar(@NotBlank @Size(max = 500) String mensaje,
                         @Size(max = 30) String tipoNotificacion,
                         List<Long> usuarioIds,
                         Long inmuebleId) {
    }

    /** Vigilante.registrarIncidencia(descripcion): lo que paso en porteria. */
    public record Incidencia(@NotBlank @Size(max = 300) String descripcion) {
    }

    /** Usuario.configurarPreferencias(tipoNotificacion): el tipo que se activa o desactiva. */
    public record Preferencia(@NotBlank @Size(max = 30) String tipoNotificacion) {
    }
}
