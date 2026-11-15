package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.model.Notificacion;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.NotificacionRepository;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.support.Proyecciones;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Caso de uso "Enviar recordatorio de reserva proxima": Notificacion.notificarReserva(reserva) automatica. */
@ExtendWith(MockitoExtension.class)
class RecordatorioReservaTest {

    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final LocalDateTime HASTA = DESDE.plusHours(1);

    @Mock
    private NotificacionRepository notificaciones;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private ReservaZonaComunRepository reservas;

    private NotificacionServiceImpl servicio() {
        return new NotificacionServiceImpl(notificaciones, usuarios, inmuebles, liquidaciones, reservas);
    }

    private ReservaView reserva(long id, long duenoId, LocalDateTime inicio) {
        return Proyecciones.de(ReservaView.class, "id", id, "zonaComunId", 7L, "nombreZonaComun", "Salon social",
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(22, 0), "zonaActiva", true,
                "fechaHoraInicio", inicio, "fechaHoraFin", inicio.plusHours(2),
                "duenoReservaId", duenoId, "nombreDueno", "Ana Perez", "inmuebleId", 1L, "direccionInmueble", "A-302");
    }

    @Test
    @DisplayName("avisa al dueno de cada reserva que empieza en la franja, con tipo RESERVA y mensaje de recordatorio")
    void recuerdaCadaReserva() {
        when(reservas.listarQueEmpiezanEntre(DESDE, HASTA)).thenReturn(List.of(
                reserva(40L, 5L, DESDE.plusMinutes(30))));
        when(usuarios.filtrarPorPreferencia(List.of(5L), "RESERVA")).thenReturn(List.of(5L));
        when(notificaciones.insertar(any())).thenReturn(70L);

        int enviados = servicio().recordarReservasProximas(DESDE, HASTA);

        assertThat(enviados).isEqualTo(1);
        ArgumentCaptor<Notificacion> enviada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).insertar(enviada.capture());
        assertThat(enviada.getValue().getTipoNotificacion()).isEqualTo("RESERVA");
        assertThat(enviada.getValue().getMensaje())
                .isEqualTo("Recordatorio: su reserva de Salon social empieza el 2026-11-16 a las 10:30.");
        verify(notificaciones).agregarDestinatarios(70L, List.of(5L));
    }

    @Test
    @DisplayName("no guarda nada si el dueno no acepta notificaciones de reserva")
    void respetaPreferencias() {
        when(reservas.listarQueEmpiezanEntre(DESDE, HASTA)).thenReturn(List.of(reserva(40L, 5L, DESDE)));
        when(usuarios.filtrarPorPreferencia(List.of(5L), "RESERVA")).thenReturn(List.of());

        assertThat(servicio().recordarReservasProximas(DESDE, HASTA)).isZero();
        verify(notificaciones, never()).insertar(any());
    }

    @Test
    @DisplayName("sin reservas en la franja no envia nada")
    void sinReservas() {
        when(reservas.listarQueEmpiezanEntre(DESDE, HASTA)).thenReturn(List.of());

        assertThat(servicio().recordarReservasProximas(DESDE, HASTA)).isZero();
        verify(notificaciones, never()).insertar(any());
    }
}
