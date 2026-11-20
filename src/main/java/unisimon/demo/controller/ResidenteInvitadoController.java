package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@RequiredArgsConstructor
public class ResidenteInvitadoController {

    private final InvitadoService invitados;
    private final AutoService autos;

    @SoloResidente
    @GetMapping("/invitados")
    public Pagina<InvitadoResponses.Invitado> listar(UsuarioAutenticado usuario, SolicitudPagina pagina,
                                                     @RequestParam(required = false) Long inmuebleId) {
        return invitados.listarInvitados(usuario.id(), inmuebleId, pagina);
    }

    @SoloResidente
    @PostMapping("/invitados")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitadoResponses.Invitado crear(UsuarioAutenticado usuario,
                                            @Valid @RequestBody InvitadoRequests.CrearInvitado solicitud,
                                            @RequestParam(required = false) Long inmuebleId) {
        return invitados.crearInvitado(usuario.id(), inmuebleId, solicitud);
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
                                            @Valid @RequestBody AutoRequests.RegistrarAuto solicitud,
                                            @RequestParam(required = false) Long inmuebleId) {
        return autos.registrarAuto(usuario.id(), inmuebleId, solicitud);
    }

    @SoloResidente
    @GetMapping("/invitados/{id}")
    public InvitadoResponses.Invitado consultar(UsuarioAutenticado usuario, @PathVariable Long id) {
        return invitados.consultarInvitado(usuario.id(), id);
    }

    @SoloResidente
    @PutMapping("/invitados/{id}")
    public InvitadoResponses.Invitado actualizar(UsuarioAutenticado usuario, @PathVariable Long id,
                                                 @Valid @RequestBody InvitadoRequests.ActualizarInvitado solicitud) {
        return invitados.actualizarInvitado(usuario.id(), id, solicitud);
    }

    /** Quita la autorizacion; el invitado sigue existiendo para los demas residentes que lo autorizaron. */
    @SoloResidente
    @DeleteMapping("/invitados/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(UsuarioAutenticado usuario, @PathVariable Long id) {
        invitados.eliminarInvitado(usuario.id(), id);
    }

    @SoloResidente
    @PutMapping("/auto")
    public AutoResponses.Auto actualizarAuto(UsuarioAutenticado usuario,
                                             @Valid @RequestBody AutoRequests.ActualizarAuto solicitud) {
        return autos.actualizarAuto(usuario.id(), solicitud);
    }

    @SoloResidente
    @DeleteMapping("/auto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAuto(UsuarioAutenticado usuario) {
        autos.eliminarAuto(usuario.id());
    }
}
