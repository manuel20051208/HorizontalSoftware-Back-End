package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ZonaComunTest {

    private ZonaComun zona(String inicio, String fin) {
        return new ZonaComun(1L, "Salon social", 10, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    @Test
    @DisplayName("horarioIncluye: inicio inclusive, fin exclusive")
    void horarioNormal() {
        ZonaComun z = zona("08:00", "20:00");

        assertThat(z.horarioIncluye(LocalTime.of(8, 0))).isTrue();
        assertThat(z.horarioIncluye(LocalTime.of(14, 30))).isTrue();
        assertThat(z.horarioIncluye(LocalTime.of(20, 0))).isFalse();
        assertThat(z.horarioIncluye(LocalTime.of(7, 59))).isFalse();
    }

    @Test
    @DisplayName("horarioIncluye entiende horarios que cruzan la medianoche")
    void horarioQueCruzaMedianoche() {
        ZonaComun z = zona("22:00", "02:00");

        assertThat(z.horarioIncluye(LocalTime.of(23, 0))).isTrue();
        assertThat(z.horarioIncluye(LocalTime.of(1, 0))).isTrue();
        assertThat(z.horarioIncluye(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    @DisplayName("horarioIncluye es falso sin hora o sin horario definido")
    void sinDatos() {
        assertThat(zona("08:00", "20:00").horarioIncluye(null)).isFalse();
        assertThat(new ZonaComun().horarioIncluye(LocalTime.NOON)).isFalse();
    }

    @Test
    @DisplayName("cambiarEstado alterna entre activa e inactiva")
    void cambiarEstado() {
        ZonaComun z = zona("08:00", "20:00");
        assertThat(z.getIsActive()).isTrue();

        z.cambiarEstado();
        assertThat(z.getIsActive()).isFalse();

        z.cambiarEstado();
        assertThat(z.getIsActive()).isTrue();
    }
}
