package unisimon.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.AdministradorOResidente;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.PasarelaPagoService;

/** Consultas de un pago que comparten el administrador y las personas del inmueble del pago. */
@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagos;
    private final PasarelaPagoService pasarela;

    @AdministradorOResidente
    @GetMapping("/{id}/comprobante")
    public PagoResponses.Comprobante comprobante(@PathVariable Long id, UsuarioAutenticado usuario,
                                                 @RequestParam(required = false) Long inmuebleId) {
        return pagos.generarComprobante(id, usuario, inmuebleId);
    }

    @AdministradorOResidente
    @GetMapping("/estado/{referencia}")
    public PagoResponses.EstadoPago estado(@PathVariable String referencia, UsuarioAutenticado usuario,
                                          @RequestParam(required = false) Long inmuebleId) {
        return pasarela.consultarEstado(referencia, usuario, inmuebleId);
    }
}
