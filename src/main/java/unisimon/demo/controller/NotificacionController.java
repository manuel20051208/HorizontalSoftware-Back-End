package unisimon.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.Autenticado;
import unisimon.demo.service.NotificacionService;

/** Notificaciones del usuario con sesion iniciada (campana del front-end). */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Autenticado
    @GetMapping
    public Pagina<NotificacionResponses.Notificacion> mias(UsuarioAutenticado usuario, SolicitudPagina pagina) {
        return notificaciones.consultarNotificacion(usuario.id(), pagina);
    }

    @Autenticado
    @PutMapping("/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(UsuarioAutenticado usuario) {
        notificaciones.marcarNotificacionLeida(usuario.id());
    }
}
