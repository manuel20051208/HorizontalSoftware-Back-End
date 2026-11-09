package unisimon.demo.mapper;

import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.repository.NotificacionRepository.LecturaView;
import unisimon.demo.repository.NotificacionRepository.NotificacionView;

import java.time.LocalDateTime;
import java.util.List;

/** Proyecciones de notificaciones -> respuestas del API. */
public final class NotificacionMapper {

    private NotificacionMapper() {
    }

    public static NotificacionResponses.Notificacion de(NotificacionView v) {
        return new NotificacionResponses.Notificacion(v.getId(), v.getMensaje(), v.getTipoNotificacion(),
                v.getFechaEnvio());
    }

    public static NotificacionResponses.Notificacion de(Long id, String mensaje, String tipo, LocalDateTime fecha) {
        return new NotificacionResponses.Notificacion(id, mensaje, tipo, fecha);
    }

    public static NotificacionResponses.Lectura lectura(Long notificacionId, List<LecturaView> filas) {
        return new NotificacionResponses.Lectura(notificacionId,
                destinatarios(filas, true), destinatarios(filas, false));
    }

    private static List<NotificacionResponses.Destinatario> destinatarios(List<LecturaView> filas, boolean leida) {
        return filas.stream()
                .filter(f -> Boolean.TRUE.equals(f.getLeida()) == leida)
                .map(f -> new NotificacionResponses.Destinatario(f.getUsuarioId(), f.getNombreCompleto()))
                .toList();
    }
}
