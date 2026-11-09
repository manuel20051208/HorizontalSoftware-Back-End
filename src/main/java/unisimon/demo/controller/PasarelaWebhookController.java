package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.annotation.Publico;
import unisimon.demo.service.PasarelaPagoService;

/**
 * Aviso que la pasarela envia al backend cuando un pago se aprueba o se rechaza.
 * Es publico (la pasarela no tiene sesion): se autentica con la llave de la pasarela en X-Api-Key.
 */
@RestController
@RequestMapping("/api/pasarela")
public class PasarelaWebhookController {

    private final PasarelaPagoService pasarela;

    public PasarelaWebhookController(PasarelaPagoService pasarela) {
        this.pasarela = pasarela;
    }

    @Publico
    @PostMapping("/webhook")
    public PagoResponses.Pago webhook(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                      @Valid @RequestBody PagoRequests.Webhook aviso) {
        return pasarela.confirmarTransaccion(apiKey, aviso);
    }
}
