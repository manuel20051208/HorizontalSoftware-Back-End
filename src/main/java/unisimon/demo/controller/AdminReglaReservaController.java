package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReglaResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.ReglaReservaService;

/** Reglas de reserva y su aplicacion a zonas. */
@RestController
@RequestMapping("/api/admin")
public class AdminReglaReservaController {

    private final ReglaReservaService reglas;

    public AdminReglaReservaController(ReglaReservaService reglas) {
        this.reglas = reglas;
    }

    @SoloAdministrador
    @PostMapping("/reglas-reserva")
    @ResponseStatus(HttpStatus.CREATED)
    public ReglaResponses.Regla crear(UsuarioAutenticado admin, @Valid @RequestBody ReglaRequests.CrearRegla s) {
        return reglas.configurarNuevaReglaReserva(admin.id(), s);
    }

    @SoloAdministrador
    @GetMapping("/reglas-reserva")
    public Pagina<ReglaResponses.Regla> listar(SolicitudPagina pagina) {
        return reglas.listar(pagina);
    }

    @SoloAdministrador
    @PutMapping("/reglas-reserva/{id}")
    public ReglaResponses.Regla actualizar(@PathVariable Long id,
                                           @Valid @RequestBody ReglaRequests.ActualizarReglas solicitud) {
        return reglas.actualizarReglas(id, solicitud);
    }

    @SoloAdministrador
    @PatchMapping("/rompimientos/{id}/aplicar")
    public ReglaResponses.Rompimiento aplicar(@PathVariable Long id) {
        return reglas.aplicarRegla(id);
    }

    @SoloAdministrador
    @PatchMapping("/rompimientos/{id}/inactivar")
    public ReglaResponses.Rompimiento inactivar(@PathVariable Long id) {
        return reglas.inactivarRegla(id);
    }
}
