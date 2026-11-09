package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaZonaComunServiceImplTest {

    @Mock
    private ReservaZonaComunRepository reservas;
    @Mock
    private ZonaComunRepository zonas;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private NotificacionService notificaciones;

    private ReservaZonaComunServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        servicio = new ReservaZonaComunServiceImpl(reservas, zonas, residentes, notificaciones, reloj);
    }

    private ContextoResidenteView residente(boolean activo, String estadoLiquidacion) {
        return Proyecciones.de(ContextoResidenteView.class, "id", 5L, "nombreCompleto", "Ana Perez",
                "active", activo, "inmuebleId", 1L, "direccionInmueble", "A-302", "inmuebleActive", true,
                "liquidacionId", estadoLiquidacion == null ? null : 3L, "liquidacionEstado", estadoLiquidacion,
                "liquidacionFechaLimite", LocalDate.of(2026, 12, 10));
    }

    private ZonaView zona(boolean activa, long reservasActuales) {
        return Proyecciones.de(ZonaView.class, "id", 7L, "nombreZonaComun", "Salon social", "capacidad", 2,
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(22, 0), "isActive", activa,
                "reservasActuales", reservasActuales);
    }

    private ReservaView reserva() {
        return Proyecciones.de(ReservaView.class, "id", 40L, "zonaComunId", 7L, "nombreZonaComun", "Salon social",
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(22, 0), "zonaActiva", true,
                "duenoReservaId", 5L, "nombreDueno", "Ana Perez", "inmuebleId", 1L, "direccionInmueble", "A-302");
    }

    private void escenarioReservable(long reservasActuales) {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona(true, reservasActuales)));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of(zona(true, reservasActuales)));
    }

    @Test
    @DisplayName("reservar guarda la reserva, notifica y devuelve la reserva creada")
    void reservarCorrecto() {
        escenarioReservable(0);
        when(reservas.existePorResidenteYZona(5L, 7L)).thenReturn(false);
        when(reservas.insertar(any(ReservaZonaComun.class))).thenReturn(40L);
        when(reservas.buscarPorId(40L)).thenReturn(Optional.of(reserva()));

        ReservaResponses.Reserva respuesta =
                servicio.reservarZonaComun(5L, new ReservaRequests.Reservar(7L, LocalTime.of(10, 0)));

        assertThat(respuesta.id()).isEqualTo(40L);
        assertThat(respuesta.enCurso()).isTrue();   // el reloj fijo marca las 15:00, dentro del horario
        verify(notificaciones).notificarReserva(40L);
    }

    @Test
    @DisplayName("no reserva fuera del horario de la zona")
    void reservarFueraDeHorario() {
        escenarioReservable(0);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                new ReservaRequests.Reservar(7L, LocalTime.of(23, 30))))
                .isInstanceOf(ConflictoException.class);
        verify(reservas, never()).insertar(any());
    }

    @Test
    @DisplayName("no reserva si la zona esta llena")
    void reservarZonaLlena() {
        escenarioReservable(2);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                new ReservaRequests.Reservar(7L, LocalTime.of(10, 0))))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("no reserva dos veces la misma zona")
    void reservarDuplicada() {
        escenarioReservable(0);
        when(reservas.existePorResidenteYZona(5L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                new ReservaRequests.Reservar(7L, LocalTime.of(10, 0))))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("un residente en mora no esta habilitado para reservar")
    void reservarEnMora() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.EN_MORA)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona(true, 0)));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of(zona(true, 0)));

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                new ReservaRequests.Reservar(7L, LocalTime.of(10, 0))))
                .isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("no reserva una zona que el inmueble no tiene asignada")
    void reservarZonaNoAsignada() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona(true, 0)));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                new ReservaRequests.Reservar(7L, LocalTime.of(10, 0))))
                .isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("solo quien reservo puede cancelar")
    void cancelar() {
        when(reservas.buscarPorId(40L)).thenReturn(Optional.of(reserva()));

        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(99L, 40L)).isInstanceOf(ProhibidoException.class);
        verify(reservas, never()).eliminar(any());

        servicio.cancelarReservaZonaComun(5L, 40L);
        verify(reservas).eliminar(40L);
    }

    @Test
    @DisplayName("cancelar una reserva inexistente es 404")
    void cancelarInexistente() {
        when(reservas.buscarPorId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(5L, 1L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("verificarDisponibilidad informa horario, cupo y habilitacion")
    void disponibilidad() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona(true, 0)));

        ReservaResponses.Disponibilidad libre = servicio.verificarDisponibilidad(5L, 7L, LocalTime.of(10, 0));
        assertThat(libre.disponible()).isTrue();
        assertThat(libre.habilitado()).isTrue();
        assertThat(libre.mensaje()).isEqualTo("Disponible");

        ReservaResponses.Disponibilidad tarde = servicio.verificarDisponibilidad(5L, 7L, LocalTime.of(23, 0));
        assertThat(tarde.horarioValido()).isFalse();
        assertThat(tarde.disponible()).isFalse();
    }
}
