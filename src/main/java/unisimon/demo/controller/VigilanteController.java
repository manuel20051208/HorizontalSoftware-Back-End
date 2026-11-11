package unisimon.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.VigilanteResponses;
import unisimon.demo.security.annotation.SoloVigilante;
import unisimon.demo.service.VigilanteService;

/** Panel de porteria. */
@RestController
@RequestMapping("/api/vigilante")
public class VigilanteController {

    private final VigilanteService vigilante;

    public VigilanteController(VigilanteService vigilante) {
        this.vigilante = vigilante;
    }

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
    @GetMapping("/invitados/{identificacion}")
    public VigilanteResponses.InvitadoConsulta invitado(@PathVariable Long identificacion) {
        return vigilante.existeInvitado(identificacion);
    }

    @SoloVigilante
    @PostMapping("/invitados/{identificacion}/ingreso")
    public VigilanteResponses.Ingreso registrarIngreso(@PathVariable Long identificacion) {
        return vigilante.registrarIngreso(identificacion);
    }
}
