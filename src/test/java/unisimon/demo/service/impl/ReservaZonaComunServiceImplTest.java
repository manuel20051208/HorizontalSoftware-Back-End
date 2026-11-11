package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.repository.RompimientoRepository;
import unisimon.demo.repository.RompimientoRepository.LimitesReservaView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** El reloj fijo marca el 2026-11-08 a las 15:00. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 15, 0);
    private static final LocalDate PASADO_MANANA = LocalDate.of(2026, 11, 10);

    @Mock
    private ReservaZonaComunRepository reservas;
    @Mock
    private ZonaComunRepository zonas;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private RompimientoRepository reglas;
    @Mock
    private NotificacionService notificaciones;

    private ReservaZonaComunServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        servicio = new ReservaZonaComunServiceImpl(reservas, zonas, residentes, reglas, notificaciones, reloj);
    }

    private ContextoResidenteView residente(boolean activo, String estadoLiquidacion) {
        return Proyecciones.de(ContextoResidenteView.class, "id", 5L, "nombreCompleto", "Ana Perez",
                "active", activo, "inmuebleId", 1L, "direccionInmueble", "A-302", "inmuebleActive", true,
                "liquidacionId", estadoLiquidacion == null ? null : 3L, "liquidacionEstado", estadoLiquidacion,
                "liquidacionFechaLimite", LocalDate.of(2026, 12, 10));
    }

    /** Salon social, abierto de 08:00 a 22:00, con cupo para 2 reservas a la vez. */
    private ZonaView zona() {
        return Proyecciones.de(ZonaView.class, "id", 7L, "nombreZonaComun", "Salon social", "capacidad", 2,
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(22, 0), "isActive", true,
                "reservasActuales", 0L);
    }

    private ReservaView reserva(LocalDateTime inicio, LocalDateTime fin) {
        return Proyecciones.de(ReservaView.class, "id", 40L, "zonaComunId", 7L, "nombreZonaComun", "Salon social",
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(22, 0), "zonaActiva", true,
                "fechaHoraInicio", inicio, "fechaHoraFin", fin,
                "duenoReservaId", 5L, "nombreDueno", "Ana Perez", "inmuebleId", 1L, "direccionInmueble", "A-302");
    }

    private LimitesReservaView limites(Integer anticipacion, Integer duracion) {
        return Proyecciones.de(LimitesReservaView.class, "anticipacionMinimaHoras", anticipacion,
                "duracionMaximaHoras", duracion);
    }

    private void escenarioReservable() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of(zona()));
    }

    private static ReservaRequests.Reservar pedir(LocalDateTime inicio, LocalDateTime fin) {
        return new ReservaRequests.Reservar(7L, inicio, fin);
    }

    @Test
    @DisplayName("reservar guarda el intervalo, notifica y devuelve la reserva PROGRAMADA")
    void reservarCorrecto() {
        escenarioReservable();
        LocalDateTime inicio = PASADO_MANANA.atTime(10, 0);
        LocalDateTime fin = PASADO_MANANA.atTime(12, 0);
        when(reservas.insertar(any(ReservaZonaComun.class))).thenReturn(40L);
        when(reservas.buscarPorId(40L)).thenReturn(Optional.of(reserva(inicio, fin)));

        ReservaResponses.Reserva respuesta = servicio.reservarZonaComun(5L, pedir(inicio, fin.plusSeconds(30)));

        ArgumentCaptor<ReservaZonaComun> guardada = ArgumentCaptor.forClass(ReservaZonaComun.class);
        verify(reservas).insertar(guardada.capture());
        assertThat(guardada.getValue().getFechaHoraInicio()).isEqualTo(inicio);
        assertThat(guardada.getValue().getFechaHoraFin()).isEqualTo(fin);   // truncada al minuto
        assertThat(respuesta.estado()).isEqualTo(ReservaZonaComun.PROGRAMADA);
        assertThat(respuesta.cancelable()).isTrue();
        assertThat(respuesta.enCurso()).isFalse();
        verify(notificaciones).notificarReserva(40L);
    }

    @Test
    @DisplayName("no reserva si el fin no es posterior al inicio (400)")
    void reservarIntervaloInvalido() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        LocalDateTime inicio = PASADO_MANANA.atTime(10, 0);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L, pedir(inicio, inicio)))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(reservas, never()).insertar(any());
    }

    @Test
    @DisplayName("no reserva un intervalo que ya empezo (400)")
    void reservarEnElPasado() {
        escenarioReservable();

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L, pedir(AHORA.minusHours(1), AHORA.plusHours(1))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("futuro");
        verify(reservas, never()).insertar(any());
    }

    @Test
    @DisplayName("no reserva fuera del horario de la zona (409)")
    void reservarFueraDeHorario() {
        escenarioReservable();

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                pedir(PASADO_MANANA.atTime(21, 0), PASADO_MANANA.atTime(23, 0))))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("horario");
        verify(reservas, never()).insertar(any());
    }

    @Test
    @DisplayName("aplica la anticipacion minima de las reglas activas de la zona (409)")
    void reservarSinAnticipacion() {
        escenarioReservable();
        when(reglas.buscarLimitesDeZona(7L)).thenReturn(limites(72, null));

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                pedir(PASADO_MANANA.atTime(10, 0), PASADO_MANANA.atTime(12, 0))))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("72 horas de anticipacion");
    }

    @Test
    @DisplayName("aplica la duracion maxima de las reglas activas de la zona (409)")
    void reservarDemasiadoLarga() {
        escenarioReservable();
        when(reglas.buscarLimitesDeZona(7L)).thenReturn(limites(1, 2));

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                pedir(PASADO_MANANA.atTime(10, 0), PASADO_MANANA.atTime(13, 0))))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("maximo 2 horas");
    }

    @Test
    @DisplayName("no reserva si en ese intervalo la zona ya esta llena (409)")
    void reservarZonaLlena() {
        escenarioReservable();
        LocalDateTime inicio = PASADO_MANANA.atTime(10, 0);
        LocalDateTime fin = PASADO_MANANA.atTime(12, 0);
        when(reservas.contarSolapadas(7L, inicio, fin)).thenReturn(2L);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L, pedir(inicio, fin)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("cupo");
    }

    @Test
    @DisplayName("no reserva si se cruza con otra reserva suya de la misma zona (409)")
    void reservarSolapada() {
        escenarioReservable();
        LocalDateTime inicio = PASADO_MANANA.atTime(10, 0);
        LocalDateTime fin = PASADO_MANANA.atTime(12, 0);
        when(reservas.existeSolapadaDeResidente(5L, 7L, inicio, fin)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L, pedir(inicio, fin)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("se cruza");
    }

    @Test
    @DisplayName("un residente en mora no esta habilitado para reservar (403)")
    void reservarEnMora() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.EN_MORA)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of(zona()));

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                pedir(PASADO_MANANA.atTime(10, 0), PASADO_MANANA.atTime(12, 0))))
                .isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("no reserva una zona que el inmueble no tiene asignada (403)")
    void reservarZonaNoAsignada() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true, Liquidacion.PENDIENTE)));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.listarActivasDeInmueble(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.reservarZonaComun(5L,
                pedir(PASADO_MANANA.atTime(10, 0), PASADO_MANANA.atTime(12, 0))))
                .isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("solo quien reservo puede cancelar, y solo antes de que empiece")
    void cancelar() {
        when(reservas.buscarPorId(40L))
                .thenReturn(Optional.of(reserva(PASADO_MANANA.atTime(10, 0), PASADO_MANANA.atTime(12, 0))));

        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(99L, 40L)).isInstanceOf(ProhibidoException.class);
        verify(reservas, never()).eliminar(any());

        servicio.cancelarReservaZonaComun(5L, 40L);
        verify(reservas).eliminar(40L);
    }

    @Test
    @DisplayName("no se cancela una reserva en curso o finalizada (409)")
    void cancelarYaEmpezada() {
        when(reservas.buscarPorId(40L)).thenReturn(Optional.of(reserva(AHORA.minusHours(1), AHORA.plusHours(1))));
        when(reservas.buscarPorId(41L)).thenReturn(Optional.of(reserva(AHORA.minusHours(3), AHORA.minusHours(1))));

        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(5L, 40L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining(ReservaZonaComun.EN_CURSO);
        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(5L, 41L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining(ReservaZonaComun.FINALIZADA);
        verify(reservas, never()).eliminar(any());
    }

    @Test
    @DisplayName("cancelar una reserva inexistente es 404")
    void cancelarInexistente() {
        when(reservas.buscarPorId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelarReservaZonaComun(5L, 1L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("verificarDisponibilidad aplica las mismas condiciones que reservar, sin guardar nada")
    void disponibilidad() {
        escenarioReservable();
        LocalDateTime inicio = PASADO_MANANA.atTime(10, 0);
        LocalDateTime fin = PASADO_MANANA.atTime(12, 0);

        ReservaResponses.Disponibilidad libre = servicio.verificarDisponibilidad(5L, 7L, inicio, fin);
        assertThat(libre.disponible()).isTrue();
        assertThat(libre.habilitado()).isTrue();
        assertThat(libre.mensaje()).isEqualTo("Disponible");

        ReservaResponses.Disponibilidad tarde = servicio.verificarDisponibilidad(5L, 7L,
                PASADO_MANANA.atTime(21, 0), PASADO_MANANA.atTime(23, 0));
        assertThat(tarde.horarioValido()).isFalse();
        assertThat(tarde.hayCupo()).isTrue();
        assertThat(tarde.disponible()).isFalse();

        when(reservas.contarSolapadas(7L, inicio, fin)).thenReturn(2L);
        ReservaResponses.Disponibilidad llena = servicio.verificarDisponibilidad(5L, 7L, inicio, fin);
        assertThat(llena.hayCupo()).isFalse();
        assertThat(llena.horarioValido()).isTrue();
        assertThat(llena.disponible()).isFalse();
        verify(reservas, never()).insertar(any());
    }

    @Test
    @DisplayName("verReservas filtra por estado con la hora actual y rechaza estados desconocidos")
    void verReservas() {
        SolicitudPagina pagina = SolicitudPagina.primera();
        assertThatThrownBy(() -> servicio.verReservas(5L, "CANCELADA", pagina))
                .isInstanceOf(SolicitudInvalidaException.class);

        when(reservas.listarPorResidente(5L, ReservaZonaComun.FINALIZADA, AHORA, 20, 0L))
                .thenReturn(List.of(reserva(AHORA.minusHours(3), AHORA.minusHours(1))));
        when(reservas.contarPorResidente(5L, ReservaZonaComun.FINALIZADA, AHORA)).thenReturn(1L);

        var resultado = servicio.verReservas(5L, " finalizada ", pagina);

        assertThat(resultado.totalElementos()).isEqualTo(1L);
        assertThat(resultado.contenido()).singleElement()
                .satisfies(r -> {
                    assertThat(r.estado()).isEqualTo(ReservaZonaComun.FINALIZADA);
                    assertThat(r.cancelable()).isFalse();
                });
    }
}
