package unisimon.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Respuestas sobre notificaciones. */
public final class NotificacionResponses {

    private NotificacionResponses() {
    }

    public record Notificacion(Long id, String mensaje, String tipoNotificacion, LocalDateTime fechaEnvio) {
    }

    /** Tipos de notificacion que el usuario quiere recibir; vacia = los recibe todos. */
    public record Preferencias(List<String> preferenciasNotificacion) {
    }

    public record Destinatario(Long usuarioId, String nombreCompleto) {
    }

    /** Quienes ya leyeron y quienes no (confirmacion de lectura para el administrador). */
    public record Lectura(Long notificacionId, List<Destinatario> leidos, List<Destinatario> pendientes) {
    }
}
