package unisimon.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.AutoResponses;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.security.annotation.AdministradorOVigilante;
import unisimon.demo.service.AutoService;
import unisimon.demo.service.InvitadoService;

/** Consultas cruzadas que usan el administrador y la porteria. */
@RestController
@RequestMapping("/api")
public class ConsultaController {

    private final InvitadoService invitados;
    private final AutoService autos;

    public ConsultaController(InvitadoService invitados, AutoService autos) {
        this.invitados = invitados;
        this.autos = autos;
    }

    @AdministradorOVigilante
    @GetMapping("/invitados/{id}/residentes")
    public Pagina<InvitadoResponses.Anfitrion> residentesDelInvitado(@PathVariable Long id, SolicitudPagina pagina) {
        return invitados.buscarRelacionResidente(id, pagina);
    }

    @AdministradorOVigilante
    @GetMapping("/autos/{id}/duenos")
    public AutoResponses.Duenos duenosDelAuto(@PathVariable Long id, @RequestParam Long inmuebleId) {
        return autos.consultarDuenos(inmuebleId, id);
    }
}
