package unisimon.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Respuestas sobre pagos y pasarela. estado del pago: PENDIENTE, APROBADO, RECHAZADO, ANULADO, REEMBOLSADO. */
public final class PagoResponses {

    private PagoResponses() {
    }

    public record Pago(Long id, Long inmuebleId, String direccionInmueble, BigDecimal monto, LocalDate fechaPago,
                       String concepto, String metodoPago, String estado, String referencia) {
    }

    /** urlCheckout: a donde hay que llevar al residente para que pague en la pasarela. */
    public record IniciarPago(Pago pago, String urlCheckout) {
    }

    public record Comprobante(String referencia, String comprobante) {
    }

    public record EstadoPago(String referencia, String estado) {
    }

    /** apiKey sale enmascarada (solo los ultimos 4 caracteres). */
    public record Pasarela(Long id, String nombrePasarela, String urlApi, String apiKey, String estado) {
    }
}
