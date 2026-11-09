package unisimon.demo.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import unisimon.demo.exception.SolicitudInvalidaException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticasTest {

    private final EstadoPagoPolicy estado = new EstadoPagoPolicy(
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("estado de pago: sin liquidaciones, al dia, por vencer y vencida")
    void estadosDePago() {
        LocalDate manana = LocalDate.of(2026, 11, 9);
        LocalDate ayer = LocalDate.of(2026, 11, 7);

        assertThat(estado.calcular(0, BigDecimal.ZERO, null, false)).isEqualTo(EstadoPagoPolicy.SIN_LIQUIDACION);
        assertThat(estado.calcular(2, BigDecimal.ZERO, null, false)).isEqualTo(EstadoPagoPolicy.AL_DIA);
        assertThat(estado.calcular(2, null, null, false)).isEqualTo(EstadoPagoPolicy.AL_DIA);
        assertThat(estado.calcular(1, BigDecimal.TEN, manana, false)).isEqualTo(EstadoPagoPolicy.POR_VENCER);
        assertThat(estado.calcular(1, BigDecimal.TEN, LocalDate.of(2026, 11, 8), false))
                .isEqualTo(EstadoPagoPolicy.POR_VENCER);   // vence hoy: aun a tiempo
        assertThat(estado.calcular(1, BigDecimal.TEN, ayer, false)).isEqualTo(EstadoPagoPolicy.VENCIDA);
        assertThat(estado.calcular(2, BigDecimal.TEN, manana, true)).isEqualTo(EstadoPagoPolicy.VENCIDA);
    }

    @Test
    @DisplayName("la contrasena nueva exige 8 caracteres, una letra y un numero")
    void politicaDeContrasena() {
        PoliticaContrasena politica = new PoliticaContrasena();

        assertThatCode(() -> politica.validar("Clave12345")).doesNotThrowAnyException();
        assertThatThrownBy(() -> politica.validar(null)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> politica.validar("abc1")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> politica.validar("soloLetrasAqui")).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("letra y un numero");
        assertThatThrownBy(() -> politica.validar("123456789")).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    @DisplayName("el generador produce contrasenas distintas que siempre cumplen la politica")
    void generador() {
        GeneradorContrasena generador = new GeneradorContrasena();
        PoliticaContrasena politica = new PoliticaContrasena();

        java.util.Set<String> vistas = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            String clave = generador.generar();
            assertThat(clave).hasSize(12);
            assertThatCode(() -> politica.validar(clave)).doesNotThrowAnyException();
            vistas.add(clave);
        }
        assertThat(vistas).hasSizeGreaterThan(190);
    }
}
