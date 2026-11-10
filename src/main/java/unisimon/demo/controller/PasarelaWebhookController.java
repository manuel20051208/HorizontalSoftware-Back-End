package unisimon.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.security.annotation.Publico;
import unisimon.demo.service.PasarelaPagoService;

import java.util.Map;

/**
 * Eventos que Wompi envia al backend (URL de eventos configurada en el dashboard de Wompi).
 * Es publico (Wompi no tiene sesion): la autenticidad se verifica con signature.checksum y el secreto de eventos,
 * y el resultado se confirma consultando la transaccion en la API de Wompi.
 * Responde 200 sin cuerpo; cualquier otro codigo hace que Wompi reintente.
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
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> evento) {
        pasarela.confirmarTransaccion(evento);
        return ResponseEntity.ok().build();
    }
}
