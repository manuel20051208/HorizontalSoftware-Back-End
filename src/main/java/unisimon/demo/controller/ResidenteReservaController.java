package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloResidente;
import unisimon.demo.service.ReservaZonaComunService;

import java.time.LocalDateTime;

/** Zonas comunes disponibles para el residente y sus reservas. */
@RestController
@RequestMapping("/api/residente")
public class ResidenteReservaController {

    private final ReservaZonaComunService reservas;

    public ResidenteReservaController(ReservaZonaComunService reservas) {
        this.reservas = reservas;
    }

    @SoloResidente
    @GetMapping("/zonas-comunes")
    public Pagina<ZonaResponses.Zona> zonasDisponibles(UsuarioAutenticado usuario, SolicitudPagina pagina) {
        return reservas.zonasDisponibles(usuario.id(), pagina);
    }

    @SoloResidente
    @GetMapping("/zonas-comunes/{id}/disponibilidad")
    public ReservaResponses.Disponibilidad disponibilidad(
            UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        return reservas.verificarDisponibilidad(usuario.id(), id, inicio, fin);
    }

    @SoloResidente
    @GetMapping("/reservas")
    public Pagina<ReservaResponses.Reserva> misReservas(UsuarioAutenticado usuario,
                                                       @RequestParam(required = false) String estado,
                                                       SolicitudPagina pagina) {
        return reservas.verReservas(usuario.id(), estado, pagina);
    }

    @SoloResidente
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponses.Reserva reservar(UsuarioAutenticado usuario,
                                             @Valid @RequestBody ReservaRequests.Reservar solicitud) {
        return reservas.reservarZonaComun(usuario.id(), solicitud);
    }

    @SoloResidente
    @DeleteMapping("/reservas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(UsuarioAutenticado usuario, @PathVariable Long id) {
        reservas.cancelarReservaZonaComun(usuario.id(), id);
    }
}
