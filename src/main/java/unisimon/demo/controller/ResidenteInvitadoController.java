package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.AutoRequests;
import unisimon.demo.dto.request.InvitadoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.AutoResponses;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloResidente;
import unisimon.demo.service.AutoService;
import unisimon.demo.service.InvitadoService;

/** Invitados y auto del residente. */
@RestController
@RequestMapping("/api/residente")
public class ResidenteInvitadoController {

    private final InvitadoService invitados;
    private final AutoService autos;

    public ResidenteInvitadoController(InvitadoService invitados, AutoService autos) {
        this.invitados = invitados;
        this.autos = autos;
    }

    @SoloResidente
    @GetMapping("/invitados")
    public Pagina<InvitadoResponses.Invitado> listar(UsuarioAutenticado usuario, SolicitudPagina pagina) {
        return invitados.listarInvitados(usuario.id(), pagina);
    }

    @SoloResidente
    @PostMapping("/invitados")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitadoResponses.Invitado crear(UsuarioAutenticado usuario,
                                            @Valid @RequestBody InvitadoRequests.CrearInvitado solicitud) {
        return invitados.crearInvitado(usuario.id(), solicitud);
    }

    @SoloResidente
    @PatchMapping("/invitados/{id}/estado")
    public InvitadoResponses.CambioEstado cambiarEstado(UsuarioAutenticado usuario, @PathVariable Long id) {
        return invitados.cambiarEstadoInvitado(usuario.id(), id);
    }

    @SoloResidente
    @PatchMapping("/invitados/estado")
    public InvitadoResponses.CambioEstadoMasivo cambiarEstadoMasivo(
            UsuarioAutenticado usuario, @Valid @RequestBody InvitadoRequests.CambiarEstadoInvitados solicitud) {
        return invitados.cambiarEstadoInvitados(usuario.id(), solicitud.invitadoIds());
    }

    @SoloResidente
    @GetMapping("/auto")
    public AutoResponses.Auto miAuto(UsuarioAutenticado usuario) {
        return autos.consultarAuto(usuario.id());
    }

    @SoloResidente
    @PostMapping("/auto")
    @ResponseStatus(HttpStatus.CREATED)
    public AutoResponses.Auto registrarAuto(UsuarioAutenticado usuario,
                                            @Valid @RequestBody AutoRequests.RegistrarAuto solicitud) {
        return autos.registrarAuto(usuario.id(), solicitud);
    }
}
