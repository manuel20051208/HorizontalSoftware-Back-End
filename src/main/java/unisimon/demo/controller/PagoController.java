package unisimon.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.AdministradorOResidente;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.PasarelaPagoService;

/** Consultas de un pago que comparten el administrador y el residente del inmueble. */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagos;
    private final PasarelaPagoService pasarela;

    public PagoController(PagoService pagos, PasarelaPagoService pasarela) {
        this.pagos = pagos;
        this.pasarela = pasarela;
    }

    @AdministradorOResidente
    @GetMapping("/{id}/comprobante")
    public PagoResponses.Comprobante comprobante(@PathVariable Long id, UsuarioAutenticado usuario) {
        return pagos.generarComprobante(id, usuario);
    }

    @AdministradorOResidente
    @GetMapping("/estado/{referencia}")
    public PagoResponses.EstadoPago estado(@PathVariable String referencia, UsuarioAutenticado usuario) {
        return pasarela.consultarEstado(referencia, usuario);
    }
}
