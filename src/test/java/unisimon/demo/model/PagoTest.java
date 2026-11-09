package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagoTest {

    private Inmueble inmueble() {
        Inmueble i = new Inmueble(1L, "A-302", null, true);
        Liquidacion l = new Liquidacion(5L, i, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                new BigDecimal("485000"), BigDecimal.ZERO, BigDecimal.ZERO);
        i.setLiquidacion(l);
        return i;
    }

    private Pago pago(BigDecimal monto) {
        return new Pago(9L, inmueble(), null, monto, LocalDate.of(2026, 11, 8), "Administracion A-302", "PSE",
                "PAGO-9-ABC");
    }

    @Test
    @DisplayName("el pago nace PENDIENTE y con la pasarela que se le indique")
    void constructor() {
        PasarelaPago pasarela = new PasarelaPago(1L, "Wompi", "https://x", "key");
        Pago p = new Pago(1L, inmueble(), pasarela, BigDecimal.TEN, null, "c", "PSE", null);

        assertThat(p.getEstado()).isEqualTo(Pago.PENDIENTE);
        assertThat(p.getPasarela()).isSameAs(pasarela);
    }

    @Test
    @DisplayName("validarMonto exige el valor exacto y mayor que cero")
    void validarMonto() {
        Liquidacion l = inmueble().getLiquidacion();

        assertThat(pago(new BigDecimal("485000")).validarMonto(l)).isTrue();
        assertThat(pago(new BigDecimal("485000.00")).validarMonto(l)).isTrue();
        assertThat(pago(new BigDecimal("400000")).validarMonto(l)).isFalse();
        assertThat(pago(BigDecimal.ZERO).validarMonto(BigDecimal.ZERO)).isFalse();
        assertThat(pago(BigDecimal.TEN).validarMonto((Liquidacion) null)).isFalse();
    }

    @Test
    @DisplayName("estaAprobado solo es cierto para APROBADO")
    void estaAprobado() {
        Pago p = pago(BigDecimal.TEN);
        assertThat(p.estaAprobado()).isFalse();

        p.setEstado(Pago.APROBADO);
        assertThat(p.estaAprobado()).isTrue();
        assertThat(p.estaAprobado(null)).isFalse();
    }

    @Test
    @DisplayName("anularPago solo funciona con pagos PENDIENTES")
    void anularPago() {
        Pago p = pago(BigDecimal.TEN);
        p.anularPago();
        assertThat(p.getEstado()).isEqualTo(Pago.ANULADO);

        Pago aprobado = pago(BigDecimal.TEN);
        aprobado.setEstado(Pago.APROBADO);
        assertThatThrownBy(aprobado::anularPago)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDIENTE");
    }

    @Test
    @DisplayName("actualizarEstado deja al dia la liquidacion vigente solo si el pago esta aprobado")
    void actualizarEstado() {
        Pago pendiente = pago(BigDecimal.TEN);
        Liquidacion sinCambio = pendiente.actualizarEstado(pendiente);
        assertThat(sinCambio.getEstado()).isEqualTo(Liquidacion.PENDIENTE);

        Pago aprobado = pago(BigDecimal.TEN);
        aprobado.setEstado(Pago.APROBADO);
        Liquidacion alDia = aprobado.actualizarEstado(aprobado);
        assertThat(alDia.getEstado()).isEqualTo(Liquidacion.AL_DIA);
    }

    @Test
    @DisplayName("generarComprobante incluye referencia, inmueble y valor, y exige un pago aprobado")
    void generarComprobante() {
        Pago p = pago(new BigDecimal("485000"));
        assertThatThrownBy(p::generarComprobante).isInstanceOf(IllegalStateException.class);

        p.setEstado(Pago.APROBADO);
        assertThat(p.generarComprobante())
                .contains("PAGO-9-ABC")
                .contains("A-302")
                .contains("485.000")
                .contains("APROBADO");
    }
}
