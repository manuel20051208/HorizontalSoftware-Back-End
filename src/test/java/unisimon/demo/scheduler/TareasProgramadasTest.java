package unisimon.demo.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Casos de uso del actor Sistema: liquidacion mensual, aviso de mora y recordatorio de reserva proxima. */
@ExtendWith(MockitoExtension.class)
class TareasProgramadasTest {

    /** 2026-12-01 00:05 en Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T05:05:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private LiquidacionService liquidaciones;
    @Mock
    private NotificacionService notificaciones;

    @Test
    @DisplayName("el dia 1 genera la liquidacion del mes para todos los inmuebles activos, con el vencimiento por defecto")
    void generaLiquidacionMensual() {
        when(liquidaciones.generarMasiva(any())).thenReturn(new LiquidacionResponses.ResultadoMasiva(3, List.of()));

        new LiquidacionScheduler(liquidaciones, notificaciones, RELOJ).generarLiquidacionMensual();

        verify(liquidaciones).generarMasiva(new LiquidacionRequests.GenerarMasiva(LocalDate.of(2026, 12, 1), null));
    }

    @Test
    @DisplayName("cada inmueble que queda en mora recibe el aviso de pago pendiente, aunque otro falle")
    void avisaLaMora() {
        when(liquidaciones.marcarVencidas()).thenReturn(List.of(1L, 2L, 3L));
        when(notificaciones.notificarPagoPendiente(anyLong())).thenAnswer(inv -> {
            if (inv.getArgument(0, Long.class) == 2L) {
                throw new ConflictoException("sin residentes");
            }
            return null;
        });

        new LiquidacionScheduler(liquidaciones, notificaciones, RELOJ).marcarVencidas();

        verify(notificaciones).notificarPagoPendiente(1L);
        verify(notificaciones).notificarPagoPendiente(2L);
        verify(notificaciones).notificarPagoPendiente(3L);
    }

    @Test
    @DisplayName("si nada vencio no se envian avisos")
    void sinMoraNoAvisa() {
        when(liquidaciones.marcarVencidas()).thenReturn(List.of());

        new LiquidacionScheduler(liquidaciones, notificaciones, RELOJ).marcarVencidas();

        verify(notificaciones, never()).notificarPagoPendiente(any());
    }

    @Test
    @DisplayName("el recordatorio revisa la franja de una hora que empieza 24 horas despues de la hora en punto")
    void recuerdaReservasDentroDe24Horas() {
        new ReservaScheduler(notificaciones, RELOJ).recordarReservasProximas();

        verify(notificaciones).recordarReservasProximas(LocalDateTime.of(2026, 12, 2, 0, 0),
                LocalDateTime.of(2026, 12, 2, 1, 0));
    }
}
