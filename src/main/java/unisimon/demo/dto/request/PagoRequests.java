package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Cuerpos de entrada de pagos y de la pasarela. */
public final class PagoRequests {

    private PagoRequests() {
    }

    /** metodoPago: PSE, TARJETA o NEQUI. El valor lo calcula el servidor (saldo pendiente), no el cliente. */
    public record IniciarPago(@NotBlank String metodoPago) {
    }

    /** Pago manual (efectivo, transferencia...). El monto debe ser igual al saldo pendiente del inmueble. */
    public record Conciliar(@NotNull @Positive BigDecimal monto, @NotBlank String metodoPago,
                            @Size(max = 100) String referencia) {
    }

    /** estado: ACTIVA o INACTIVA. */
    public record ConfigurarPasarela(@NotBlank @Size(max = 60) String nombrePasarela,
                                     @NotBlank @Size(max = 300) String urlApi,
                                     @NotBlank @Size(max = 200) String apiKey,
                                     @NotBlank String estado) {
    }
}
