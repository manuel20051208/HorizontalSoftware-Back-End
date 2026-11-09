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
import unisimon.demo.dto.request.ZonaRequests;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.ZonaComunService;

import java.util.List;

/** Zonas comunes (administracion). */
@RestController
@RequestMapping("/api/admin/zonas-comunes")
public class AdminZonaComunController {

    private final ZonaComunService zonas;

    public AdminZonaComunController(ZonaComunService zonas) {
        this.zonas = zonas;
    }

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZonaResponses.Zona crear(@Valid @RequestBody ZonaRequests.CrearZona solicitud) {
        return zonas.crearZona(solicitud);
    }

    @SoloAdministrador
    @GetMapping
    public List<ZonaResponses.Zona> listar() {
        return zonas.listar();
    }

    @SoloAdministrador
    @PatchMapping("/{id}/estado")
    public ZonaResponses.Zona cambiarEstado(@PathVariable Long id) {
        return zonas.cambiarEstado(id);
    }
}
