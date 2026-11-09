package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LiquidacionTest {

    private Liquidacion liquidacion(LocalDate fechaLimite) {
        return new Liquidacion(1L, new Inmueble(), LocalDate.of(2026, 11, 1), fechaLimite,
                new BigDecimal("485000"), new BigDecimal("20000"), new BigDecimal("30000"));
    }

    @Test
    @DisplayName("el constructor calcula el total y la deja PENDIENTE")
    void constructorCalculaTotal() {
        Liquidacion l = liquidacion(LocalDate.now().plusDays(5));

        assertThat(l.getTotal()).isEqualByComparingTo("535000");
        assertThat(l.getEstado()).isEqualTo(Liquidacion.PENDIENTE);
        assertThat(l.getFechaGeneracion()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("calcularTotal suma cuota + servicios + multas y trata null como cero")
    void calcularTotal() {
        Liquidacion l = new Liquidacion();
        l.setCuotaAdministracion(new BigDecimal("100"));
        l.setValorServiciosComunes(null);
        l.setValorMultas(new BigDecimal("5"));

        assertThat(l.calcularTotal()).isEqualByComparingTo("105");
        assertThat(l.getTotal()).isEqualByComparingTo("105");
    }

    @Test
    @DisplayName("estaVencida solo si paso la fecha limite y no esta al dia")
    void estaVencida() {
        assertThat(liquidacion(LocalDate.now().minusDays(1)).estaVencida()).isTrue();
        assertThat(liquidacion(LocalDate.now()).estaVencida()).isFalse();
        assertThat(liquidacion(LocalDate.now().plusDays(3)).estaVencida()).isFalse();

        Liquidacion pagada = liquidacion(LocalDate.now().minusDays(10));
        pagada.marcarAlDia();
        assertThat(pagada.estaVencida()).isFalse();
    }

    @Test
    @DisplayName("marcarEnMora y marcarAlDia cambian el estado")
    void cambiosDeEstado() {
        Liquidacion l = liquidacion(LocalDate.now());

        l.marcarEnMora();
        assertThat(l.getEstado()).isEqualTo(Liquidacion.EN_MORA);

        l.marcarAlDia();
        assertThat(l.getEstado()).isEqualTo(Liquidacion.AL_DIA);
    }
}
