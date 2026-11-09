package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.NotificacionService;

/** Notificaciones enviadas por el administrador y su confirmacion de lectura. */
@RestController
@RequestMapping("/api/admin/notificaciones")
public class AdminNotificacionController {

    private final NotificacionService notificaciones;

    public AdminNotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificacionResponses.Notificacion enviar(@Valid @RequestBody NotificacionRequests.Enviar solicitud) {
        return notificaciones.enviarNotificacion(solicitud);
    }

    @SoloAdministrador
    @GetMapping("/{id}/lectura")
    public NotificacionResponses.Lectura lectura(@PathVariable Long id) {
        return notificaciones.consultarConfirmacionLectura(id);
    }
}
