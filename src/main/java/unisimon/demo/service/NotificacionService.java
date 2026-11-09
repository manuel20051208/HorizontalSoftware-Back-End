package unisimon.demo.service;

import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.response.NotificacionResponses;

import java.util.List;
import java.util.Optional;

/** Envio, consulta y lectura de notificaciones. */
public interface NotificacionService {

    /** Notificacion.enviarNotificacion(): mensaje libre del administrador. */
    NotificacionResponses.Notificacion enviarNotificacion(NotificacionRequests.Enviar solicitud);

    /** Notificacion.notificarPagoPendiente(inmueble): recordatorio de pago a los residentes del inmueble. */
    NotificacionResponses.Notificacion notificarPagoPendiente(Long inmuebleId);

    /** Notificacion.notificarReserva(reserva). Automatica: vacio si no hay a quien avisar. */
    Optional<NotificacionResponses.Notificacion> notificarReserva(Long reservaId);

    /** Notificacion.notificarActualizacionDePago(inmueble, usuarios): avisa el resultado de un pago. Automatica. */
    Optional<NotificacionResponses.Notificacion> notificarActualizacionDePago(Long inmuebleId);

    /** Notificacion.notificarVisita(inmueble). Automatica: vacio si el inmueble no tiene residentes activos. */
    Optional<NotificacionResponses.Notificacion> notificarVisita(Long inmuebleId, String nombreVisitante);

    /** Residente.consultarNotificacion(inmueble, id): las notificaciones recibidas por el usuario. */
    List<NotificacionResponses.Notificacion> consultarNotificacion(Long usuarioId);

    /** Usuario.marcarNotificacionLeida(inmueble, id) */
    void marcarNotificacionLeida(Long usuarioId);

    /** Administrador.consultarConfirmacionLectura(): quienes ya leyeron una notificacion. */
    NotificacionResponses.Lectura consultarConfirmacionLectura(Long notificacionId);
}
