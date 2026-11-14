package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.Autenticado;
import unisimon.demo.service.CuentaService;
import unisimon.demo.service.NotificacionService;

/** Notificaciones del usuario con sesion iniciada (campana del front-end). */
@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificaciones;
    private final CuentaService cuentas;

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

    @Autenticado
    @GetMapping("/preferencias")
    public NotificacionResponses.Preferencias preferencias(UsuarioAutenticado usuario) {
        return cuentas.consultarPreferencias(usuario.id());
    }

    @Autenticado
    @PutMapping("/preferencias")
    public NotificacionResponses.Preferencias configurarPreferencias(
            UsuarioAutenticado usuario, @Valid @RequestBody NotificacionRequests.Preferencia solicitud) {
        return cuentas.configurarPreferencias(usuario.id(), solicitud.tipoNotificacion());
    }
}
