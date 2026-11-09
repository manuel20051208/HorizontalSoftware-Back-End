package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasarelaPagoTest {

    private PasarelaPago pasarela() {
        return new PasarelaPago(1L, "Wompi", "https://checkout.wompi.co/p/", "pub_test_123");
    }

    private Pago pago(Long id, BigDecimal monto) {
        return new Pago(id, new Inmueble(1L, "A-302", null, true), null, monto, null, "c", "PSE", null);
    }

    @Test
    @DisplayName("iniciarTransaccion arma la URL del checkout y deja referencia y pasarela en el pago")
    void iniciarTransaccion() {
        PasarelaPago pasarela = pasarela();
        Pago pago = pago(7L, new BigDecimal("485000"));

        String url = pasarela.iniciarTransaccion(pago);

        assertThat(pago.getReferencia()).startsWith("PAGO-7-");
        assertThat(pago.getPasarela()).isSameAs(pasarela);
        assertThat(url)
                .startsWith("https://checkout.wompi.co/p/?public-key=pub_test_123")
                .contains("currency=COP")
                .contains("amount-in-cents=48500000")
                .contains("reference=" + pago.getReferencia());
    }

    @Test
    @DisplayName("iniciarTransaccion conserva la referencia que el pago ya tenia")
    void conservaReferencia() {
        Pago pago = pago(7L, BigDecimal.TEN);
        pago.setReferencia("MI-REF");

        pasarela().iniciarTransaccion(pago);

        assertThat(pago.getReferencia()).isEqualTo("MI-REF");
    }

    @Test
    @DisplayName("iniciarTransaccion falla con pasarela inactiva, pago sin guardar o pago no pendiente")
    void validaciones() {
        PasarelaPago inactiva = pasarela();
        inactiva.setEstado(PasarelaPago.INACTIVA);
        assertThatThrownBy(() -> inactiva.iniciarTransaccion(pago(1L, BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inactiva");

        assertThatThrownBy(() -> pasarela().iniciarTransaccion(pago(null, BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class);

        Pago aprobado = pago(1L, BigDecimal.TEN);
        aprobado.setEstado(Pago.APROBADO);
        assertThatThrownBy(() -> pasarela().iniciarTransaccion(aprobado))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PENDIENTE");
    }

    @Test
    @DisplayName("reembolsar solo aplica a pagos aprobados")
    void reembolsar() {
        Pago pendiente = pago(1L, BigDecimal.TEN);
        assertThat(pasarela().reembolsar(pendiente)).isFalse();
        assertThat(pendiente.getEstado()).isEqualTo(Pago.PENDIENTE);

        Pago aprobado = pago(2L, BigDecimal.TEN);
        aprobado.setEstado(Pago.APROBADO);
        assertThat(pasarela().reembolsar(aprobado)).isTrue();
        assertThat(aprobado.getEstado()).isEqualTo(Pago.REEMBOLSADO);

        assertThat(pasarela().reembolsar(null)).isFalse();
    }
}
