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
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloResidente;
import unisimon.demo.service.EstadoCuentaService;
import unisimon.demo.service.PagoService;

import java.util.List;

/** Estado de cuenta y pagos del residente sobre el apartamento que elige con {@code ?inmuebleId=}. */
@RestController
@RequestMapping("/api/residente")
@RequiredArgsConstructor
public class ResidenteCuentaController {

    private final EstadoCuentaService estadoCuenta;
    private final PagoService pagos;

    /** Selector de apartamento del residente: todos los inmuebles a los que esta relacionado. */
    @SoloResidente
    @GetMapping("/inmuebles")
    public List<UsuarioResponses.InmuebleResumen> inmuebles(UsuarioAutenticado usuario) {
        return estadoCuenta.inmuebles(usuario.id());
    }

    @SoloResidente
    @GetMapping("/mi-cuenta")
    public InmuebleResponses.EstadoCuenta miCuenta(UsuarioAutenticado usuario,
                                                  @RequestParam(required = false) Long inmuebleId) {
        return estadoCuenta.consultarEstadoCuenta(usuario.id(), inmuebleId);
    }

    @SoloResidente
    @GetMapping("/pagos")
    public Pagina<PagoResponses.Pago> historial(UsuarioAutenticado usuario, SolicitudPagina pagina,
                                                @RequestParam(required = false) Long inmuebleId) {
        return pagos.consultarHistorialPago(usuario.id(), inmuebleId, pagina);
    }

    @SoloResidente
    @PostMapping("/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponses.IniciarPago pagar(UsuarioAutenticado usuario,
                                           @Valid @RequestBody PagoRequests.IniciarPago solicitud,
                                           @RequestParam(required = false) Long inmuebleId) {
        return pagos.registrarPago(usuario.id(), inmuebleId, solicitud);
    }

    @SoloResidente
    @GetMapping("/pagos/{id}")
    public PagoResponses.Pago consultarPago(UsuarioAutenticado usuario, @PathVariable Long id,
                                           @RequestParam(required = false) Long inmuebleId) {
        return pagos.consultarPagoDeResidente(usuario.id(), inmuebleId, id);
    }
}
