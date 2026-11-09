package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloResidente;
import unisimon.demo.service.EstadoCuentaService;
import unisimon.demo.service.PagoService;

import java.util.List;

/** Estado de cuenta y pagos del residente. */
@RestController
@RequestMapping("/api/residente")
public class ResidenteCuentaController {

    private final EstadoCuentaService estadoCuenta;
    private final PagoService pagos;

    public ResidenteCuentaController(EstadoCuentaService estadoCuenta, PagoService pagos) {
        this.estadoCuenta = estadoCuenta;
        this.pagos = pagos;
    }

    @SoloResidente
    @GetMapping("/mi-cuenta")
    public InmuebleResponses.EstadoCuenta miCuenta(UsuarioAutenticado usuario) {
        return estadoCuenta.consultarEstadoCuenta(usuario.id());
    }

    @SoloResidente
    @GetMapping("/pagos")
    public List<PagoResponses.Pago> historial(UsuarioAutenticado usuario) {
        return pagos.consultarHistorialPago(usuario.id());
    }

    @SoloResidente
    @PostMapping("/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponses.IniciarPago pagar(UsuarioAutenticado usuario,
                                           @Valid @RequestBody PagoRequests.IniciarPago solicitud) {
        return pagos.registrarPago(usuario.id(), solicitud);
    }

    @SoloResidente
    @GetMapping("/pagos/{id}")
    public PagoResponses.Pago consultarPago(UsuarioAutenticado usuario, @PathVariable Long id) {
        return pagos.consultarPagoDeResidente(usuario.id(), id);
    }
}
