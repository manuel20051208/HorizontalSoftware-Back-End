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
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloResidente;
import unisimon.demo.service.ReservaZonaComunService;
import unisimon.demo.service.ZonaComunService;

import java.time.LocalTime;
import java.util.List;

/** Zonas comunes disponibles para el residente y sus reservas. */
@RestController
@RequestMapping("/api/residente")
public class ResidenteReservaController {

    private final ZonaComunService zonas;
    private final ReservaZonaComunService reservas;
    private final ResidenteRepository residentes;

    public ResidenteReservaController(ZonaComunService zonas, ReservaZonaComunService reservas,
                                      ResidenteRepository residentes) {
        this.zonas = zonas;
        this.reservas = reservas;
        this.residentes = residentes;
    }

    @SoloResidente
    @GetMapping("/zonas-comunes")
    public List<ZonaResponses.Zona> zonasDisponibles(UsuarioAutenticado usuario) {
        Long inmuebleId = residentes.buscarContexto(usuario.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"))
                .getInmuebleId();
        return zonas.mostrarZonasComunes(inmuebleId);
    }

    @SoloResidente
    @GetMapping("/zonas-comunes/{id}/disponibilidad")
    public ReservaResponses.Disponibilidad disponibilidad(
            UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora) {
        return reservas.verificarDisponibilidad(usuario.id(), id, hora);
    }

    @SoloResidente
    @GetMapping("/reservas")
    public List<ReservaResponses.Reserva> misReservas(UsuarioAutenticado usuario) {
        return reservas.verReservas(usuario.id());
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
