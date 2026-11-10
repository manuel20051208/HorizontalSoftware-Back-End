package unisimon.demo.service;

import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;

import java.util.Map;

/** Configuracion de la pasarela y procesamiento de lo que ella reporta. */
public interface PasarelaPagoService {

    PagoResponses.Pasarela obtener();

    PagoResponses.Pasarela configurar(PagoRequests.ConfigurarPasarela solicitud);

    /**
     * PasarelaPago.confirmarTransaccion(referencia): recibe el evento de Wompi (webhook), verifica su firma con el
     * secreto de eventos, confirma la transaccion contra la API de Wompi y actualiza el pago. Idempotente.
     */
    void confirmarTransaccion(Map<String, Object> evento);

    /** PasarelaPago.consultarEstado(referencia) */
    PagoResponses.EstadoPago consultarEstado(String referencia, UsuarioAutenticado usuario);

    /** PasarelaPago.reembolsar(pago): solo pagos aprobados. */
    PagoResponses.Pago reembolsar(Long pagoId);
}
