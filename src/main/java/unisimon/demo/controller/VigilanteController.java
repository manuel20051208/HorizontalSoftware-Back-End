package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.VigilanteResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloVigilante;
import unisimon.demo.service.VigilanteService;

/** Panel de porteria. */
@RestController
@RequestMapping("/api/vigilante")
@RequiredArgsConstructor
public class VigilanteController {

    private final VigilanteService vigilante;

    @SoloVigilante
    @GetMapping("/reservas")
    public Pagina<ReservaResponses.Reserva> reservas(@RequestParam(required = false) String estado,
                                                    SolicitudPagina pagina) {
        return vigilante.consultarReservas(estado, pagina);
    }

    @SoloVigilante
    @GetMapping("/zonas-comunes/acceso")
    public VigilanteResponses.Acceso acceso(@RequestParam Long identificacion) {
        return vigilante.verificarAccesoZonaComun(identificacion);
    }

    @SoloVigilante
    @GetMapping("/invitados")
    public Pagina<InvitadoResponses.Invitado> invitados(SolicitudPagina pagina) {
        return vigilante.consultarInvitados(pagina);
    }

    @SoloVigilante
    @GetMapping("/invitados/{identificacion}")
    public VigilanteResponses.InvitadoConsulta invitado(@PathVariable Long identificacion) {
        return vigilante.existeInvitado(identificacion);
    }

    @SoloVigilante
    @PostMapping("/invitados/{identificacion}/ingreso")
    public VigilanteResponses.Ingreso registrarIngreso(@PathVariable Long identificacion) {
        return vigilante.registrarIngreso(identificacion);
    }

    @SoloVigilante
    @PostMapping("/incidencias")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificacionResponses.Notificacion registrarIncidencia(
            UsuarioAutenticado usuario, @Valid @RequestBody NotificacionRequests.Incidencia solicitud) {
        return vigilante.registrarIncidencia(usuario.id(), solicitud.descripcion());
    }
}
