package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final LocalDate DIA = LocalDate.of(2026, 11, 20);

    private ReservaZonaComun reserva(LocalTime horaInicioZona, LocalTime horaFinZona,
                                     LocalDateTime inicio, LocalDateTime fin) {
        ZonaComun zona = new ZonaComun(1L, "Salon", 5, horaInicioZona, horaFinZona);
        return new ReservaZonaComun(null, null, zona, inicio, fin);
    }

    private ReservaZonaComun deDiezADoce() {
        return reserva(LocalTime.of(8, 0), LocalTime.of(20, 0), DIA.atTime(10, 0), DIA.atTime(12, 0));
    }

    @Test
    @DisplayName("el estado es PROGRAMADA antes del inicio, EN_CURSO durante y FINALIZADA desde el fin")
    void estado() {
        ReservaZonaComun r = deDiezADoce();

        assertThat(r.estado(DIA.atTime(9, 59))).isEqualTo(ReservaZonaComun.PROGRAMADA);
        assertThat(r.estado(DIA.atTime(10, 0))).isEqualTo(ReservaZonaComun.EN_CURSO);
        assertThat(r.estado(DIA.atTime(11, 59))).isEqualTo(ReservaZonaComun.EN_CURSO);
        assertThat(r.estado(DIA.atTime(12, 0))).isEqualTo(ReservaZonaComun.FINALIZADA);
    }

    @Test
    @DisplayName("solo se cancela una reserva PROGRAMADA")
    void cancelable() {
        ReservaZonaComun r = deDiezADoce();

        assertThat(r.sePuedeCancelar(DIA.atTime(9, 0))).isTrue();
        assertThat(r.sePuedeCancelar(DIA.atTime(10, 30))).isFalse();
        assertThat(r.sePuedeCancelar(DIA.atTime(13, 0))).isFalse();
    }

    @Test
    @DisplayName("el intervalo es valido solo si el fin es posterior al inicio")
    void intervalo() {
        assertThat(deDiezADoce().intervaloValido()).isTrue();
        assertThat(reserva(LocalTime.of(8, 0), LocalTime.of(20, 0), DIA.atTime(10, 0), DIA.atTime(10, 0))
                .intervaloValido()).isFalse();
        assertThat(new ReservaZonaComun().intervaloValido()).isFalse();
    }

    @Test
    @DisplayName("validarHorario exige que todo el intervalo caiga en una apertura de la zona")
    void validarHorarioDiurno() {
        LocalTime abre = LocalTime.of(8, 0);
        LocalTime cierra = LocalTime.of(20, 0);

        assertThat(deDiezADoce().validarHorario()).isTrue();
        assertThat(reserva(abre, cierra, DIA.atTime(8, 0), DIA.atTime(20, 0)).validarHorario()).isTrue();
        assertThat(reserva(abre, cierra, DIA.atTime(7, 30), DIA.atTime(9, 0)).validarHorario()).isFalse();
        assertThat(reserva(abre, cierra, DIA.atTime(19, 0), DIA.atTime(20, 30)).validarHorario()).isFalse();
        assertThat(reserva(abre, cierra, DIA.atTime(19, 0), DIA.plusDays(1).atTime(9, 0)).validarHorario()).isFalse();
        assertThat(new ReservaZonaComun().validarHorario()).isFalse();
    }

    @Test
    @DisplayName("con horario que cruza la medianoche la reserva puede terminar al dia siguiente")
    void validarHorarioNocturno() {
        LocalTime abre = LocalTime.of(20, 0);
        LocalTime cierra = LocalTime.of(2, 0);

        assertThat(reserva(abre, cierra, DIA.atTime(22, 0), DIA.plusDays(1).atTime(1, 0)).validarHorario()).isTrue();
        assertThat(reserva(abre, cierra, DIA.atTime(0, 30), DIA.atTime(1, 30)).validarHorario()).isTrue();
        assertThat(reserva(abre, cierra, DIA.atTime(23, 0), DIA.plusDays(1).atTime(3, 0)).validarHorario()).isFalse();
        assertThat(reserva(abre, cierra, DIA.atTime(12, 0), DIA.atTime(13, 0)).validarHorario()).isFalse();
    }

    @Test
    @DisplayName("las reglas de la zona limitan la anticipacion minima y la duracion maxima")
    void reglas() {
        ReservaZonaComun r = deDiezADoce();

        assertThat(r.cumpleAnticipacion(DIA.atTime(7, 0), 3)).isTrue();
        assertThat(r.cumpleAnticipacion(DIA.atTime(7, 1), 3)).isFalse();
        assertThat(r.cumpleAnticipacion(DIA.atTime(9, 59), null)).isTrue();
        assertThat(r.cumpleDuracionMaxima(2)).isTrue();
        assertThat(r.cumpleDuracionMaxima(1)).isFalse();
        assertThat(r.cumpleDuracionMaxima(null)).isTrue();
    }
}
