package unisimon.demo.service;

import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;

/** Configuracion de la pasarela y procesamiento de lo que ella reporta. */
public interface PasarelaPagoService {

    PagoResponses.Pasarela obtener();

    PagoResponses.Pasarela configurar(PagoRequests.ConfigurarPasarela solicitud);

    /**
     * PasarelaPago.confirmarTransaccion(referencia): recibe el aviso de la pasarela (webhook), lo autentica con la
     * llave de la pasarela y actualiza el pago. Idempotente.
     */
    PagoResponses.Pago confirmarTransaccion(String apiKey, PagoRequests.Webhook aviso);

    /** PasarelaPago.consultarEstado(referencia) */
    PagoResponses.EstadoPago consultarEstado(String referencia, UsuarioAutenticado usuario);

    /** PasarelaPago.reembolsar(pago): solo pagos aprobados. */
    PagoResponses.Pago reembolsar(Long pagoId);
}
