package unisimon.demo.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unisimon.demo.service.NotificacionService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Tarea programada del caso de uso "Enviar recordatorio de reserva proxima" (actor Sistema): cada hora avisa a los
 * residentes cuyas reservas empiezan dentro de {@link #ANTELACION_HORAS} horas. Cada ejecucion revisa una franja
 * de una hora distinta, asi que cada reserva recibe un solo recordatorio.
 */
@Component
@RequiredArgsConstructor
public class ReservaScheduler {

    static final int ANTELACION_HORAS = 24;

    private static final Logger log = LoggerFactory.getLogger(ReservaScheduler.class);

    private final NotificacionService notificaciones;
    private final Clock reloj;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Bogota")
    public void recordarReservasProximas() {
        LocalDateTime desde = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS).plusHours(ANTELACION_HORAS);
        int enviados = notificaciones.recordarReservasProximas(desde, desde.plusHours(1));
        log.info("Recordatorios de reserva enviados: {}", enviados);
    }
}
