package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.PasarelaPagoService;

/** Pagos recibidos y configuracion de la pasarela. */
@RestController
@RequestMapping("/api/admin")
public class AdminPagoController {

    private final PagoService pagos;
    private final PasarelaPagoService pasarela;

    public AdminPagoController(PagoService pagos, PasarelaPagoService pasarela) {
        this.pagos = pagos;
        this.pasarela = pasarela;
    }

    @SoloAdministrador
    @GetMapping("/pagos")
    public Pagina<PagoResponses.Pago> listar(@RequestParam(required = false) Long inmuebleId,
                                             @RequestParam(required = false) String estado,
                                             SolicitudPagina pagina) {
        return pagos.listar(inmuebleId, estado, pagina);
    }

    @SoloAdministrador
    @PostMapping("/pagos/{id}/anular")
    public PagoResponses.Pago anular(@PathVariable Long id) {
        return pagos.anularPago(id);
    }

    @SoloAdministrador
    @PostMapping("/pagos/{id}/reembolso")
    public PagoResponses.Pago reembolsar(@PathVariable Long id) {
        return pasarela.reembolsar(id);
    }

    @SoloAdministrador
    @GetMapping("/pasarela")
    public PagoResponses.Pasarela obtenerPasarela() {
        return pasarela.obtener();
    }

    @SoloAdministrador
    @PutMapping("/pasarela")
    public PagoResponses.Pasarela configurarPasarela(@Valid @RequestBody PagoRequests.ConfigurarPasarela s) {
        return pasarela.configurar(s);
    }
}
