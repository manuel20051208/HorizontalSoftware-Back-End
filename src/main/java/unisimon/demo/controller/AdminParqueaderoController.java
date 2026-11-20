package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.ParqueaderoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ParqueaderoResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.ParqueaderoService;

/** Parqueaderos de los inmuebles (administracion). */
@RestController
@RequestMapping("/api/admin/parqueaderos")
@RequiredArgsConstructor
public class AdminParqueaderoController {

    private final ParqueaderoService parqueaderos;

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParqueaderoResponses.Parqueadero crear(@Valid @RequestBody ParqueaderoRequests.CrearParqueadero s) {
        return parqueaderos.crear(s);
    }

    @SoloAdministrador
    @GetMapping
    public Pagina<ParqueaderoResponses.Parqueadero> listar(SolicitudPagina pagina) {
        return parqueaderos.listar(pagina);
    }

    @SoloAdministrador
    @GetMapping("/{id}")
    public ParqueaderoResponses.Parqueadero consultar(@PathVariable Long id) {
        return parqueaderos.consultar(id);
    }

    @SoloAdministrador
    @PutMapping("/{id}")
    public ParqueaderoResponses.Parqueadero actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ParqueaderoRequests.ActualizarParqueadero s) {
        return parqueaderos.actualizar(id, s);
    }

    @SoloAdministrador
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        parqueaderos.eliminar(id);
    }
}
