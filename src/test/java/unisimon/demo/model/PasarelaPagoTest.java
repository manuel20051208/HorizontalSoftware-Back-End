package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasarelaPagoTest {

    private static final String SECRETO = "test_integrity_abc";

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

        String url = pasarela.iniciarTransaccion(pago, SECRETO);

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

        pasarela().iniciarTransaccion(pago, SECRETO);

        assertThat(pago.getReferencia()).isEqualTo("MI-REF");
    }

    @Test
    @DisplayName("iniciarTransaccion falla con pasarela inactiva, pago sin guardar o pago no pendiente")
    void validaciones() {
        PasarelaPago inactiva = pasarela();
        inactiva.setEstado(PasarelaPago.INACTIVA);
        assertThatThrownBy(() -> inactiva.iniciarTransaccion(pago(1L, BigDecimal.TEN), SECRETO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inactiva");

        assertThatThrownBy(() -> pasarela().iniciarTransaccion(pago(null, BigDecimal.TEN), SECRETO))
                .isInstanceOf(IllegalArgumentException.class);

        Pago aprobado = pago(1L, BigDecimal.TEN);
        aprobado.setEstado(Pago.APROBADO);
        assertThatThrownBy(() -> pasarela().iniciarTransaccion(aprobado, SECRETO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PENDIENTE");
    }

    @Test
    @DisplayName("iniciarTransaccion firma el checkout con SHA-256(referencia + centavos + COP + secreto)")
    void firmaIntegridad() {
        Pago pago = pago(7L, new BigDecimal("485000"));
        pago.setReferencia("PAGO-7-ABC12345");

        String url = pasarela().iniciarTransaccion(pago, SECRETO);

        // Valor calculado fuera de Java: printf '%s' PAGO-7-ABC12345 48500000 COP test_integrity_abc | sha256sum
        String esperada = "db0ad3ba7dc25ded4c980346e1f65af35488872610ff0ae0832b1d8692f840a4";
        assertThat(url).endsWith("&signature:integrity=" + esperada).doesNotContain(SECRETO);
    }

    @Test
    @DisplayName("iniciarTransaccion no arma un checkout sin firmar si falta el secreto")
    void sinSecreto() {
        assertThatThrownBy(() -> pasarela().iniciarTransaccion(pago(1L, BigDecimal.TEN), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("secreto");
        assertThatThrownBy(() -> pasarela().iniciarTransaccion(pago(1L, BigDecimal.TEN), "  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("secreto");
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
