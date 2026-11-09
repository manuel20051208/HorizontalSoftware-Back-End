package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservaZonaComunTest {

    private Residente residente(boolean activo, boolean inmuebleActivo, String estadoLiquidacion, LocalDate limite) {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, inmuebleActivo);
        if (estadoLiquidacion != null) {
            Liquidacion l = new Liquidacion(1L, inmueble, LocalDate.of(2026, 11, 1), limite, BigDecimal.TEN,
                    BigDecimal.ZERO, BigDecimal.ZERO);
            l.setEstado(estadoLiquidacion);
            inmueble.setLiquidacion(l);
        }
        Residente r = new Residente();
        r.setId(1L);
        r.setActive(activo);
        r.setInmueble(inmueble);
        return r;
    }

    private ReservaZonaComun reserva() {
        ZonaComun zona = new ZonaComun(1L, "Salon", 5, LocalTime.of(8, 0), LocalTime.of(20, 0));
        return new ReservaZonaComun(null, null, zona);
    }

    @Test
    @DisplayName("esta habilitado con cuenta e inmueble activos y administracion al dia o por vencer")
    void habilitado() {
        LocalDate futuro = LocalDate.now().plusDays(5);

        assertThat(reserva().estaHabilitado(residente(true, true, Liquidacion.PENDIENTE, futuro))).isTrue();
        assertThat(reserva().estaHabilitado(residente(true, true, Liquidacion.AL_DIA, futuro))).isTrue();
        assertThat(reserva().estaHabilitado(residente(true, true, null, null))).isTrue();
    }

    @Test
    @DisplayName("no esta habilitado si la cuenta o el inmueble estan inactivos")
    void inactivos() {
        LocalDate futuro = LocalDate.now().plusDays(5);

        assertThat(reserva().estaHabilitado(residente(false, true, Liquidacion.AL_DIA, futuro))).isFalse();
        assertThat(reserva().estaHabilitado(residente(true, false, Liquidacion.AL_DIA, futuro))).isFalse();
        assertThat(reserva().estaHabilitado(null)).isFalse();
    }

    @Test
    @DisplayName("no esta habilitado si debe la administracion (en mora o vencida)")
    void enMora() {
        assertThat(reserva().estaHabilitado(residente(true, true, Liquidacion.EN_MORA, LocalDate.now().plusDays(5)))).isFalse();
        assertThat(reserva().estaHabilitado(residente(true, true, Liquidacion.PENDIENTE, LocalDate.now().minusDays(1)))).isFalse();
    }

    @Test
    @DisplayName("validarHorario consulta el horario de la zona")
    void validarHorario() {
        assertThat(reserva().validarHorario(LocalTime.of(10, 0))).isTrue();
        assertThat(reserva().validarHorario(LocalTime.of(21, 0))).isFalse();
        assertThat(new ReservaZonaComun().validarHorario(LocalTime.NOON)).isFalse();
    }
}
