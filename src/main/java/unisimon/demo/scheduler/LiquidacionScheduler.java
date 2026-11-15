package unisimon.demo.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.exception.ApiException;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Tareas programadas de las liquidaciones (casos de uso del actor Sistema):
 * <ul>
 *   <li>Generar liquidacion mensual: el dia 1 de cada mes genera el periodo de todos los inmuebles activos.</li>
 *   <li>Enviar notificaciones de vencimiento y mora: cada madrugada pasa a EN_MORA las liquidaciones que vencieron
 *       sin pagarse y le avisa a cada inmueble que quedo en mora.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LiquidacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionScheduler.class);

    private final LiquidacionService liquidaciones;
    private final NotificacionService notificaciones;
    private final Clock reloj;

    @Scheduled(cron = "0 0 0 1 * *", zone = "America/Bogota")
    public void generarLiquidacionMensual() {
        LiquidacionResponses.ResultadoMasiva resultado = liquidaciones.generarMasiva(
                new LiquidacionRequests.GenerarMasiva(LocalDate.now(reloj), null));
        log.info("Liquidacion mensual: {} generadas, {} omitidas", resultado.generadas(),
                resultado.omitidas().size());
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Bogota")
    public void marcarVencidas() {
        List<Long> inmueblesEnMora = liquidaciones.marcarVencidas();
        int avisados = 0;
        for (Long inmuebleId : inmueblesEnMora) {
            try {
                notificaciones.notificarPagoPendiente(inmuebleId);
                avisados++;
            } catch (ApiException e) {
                log.warn("No se pudo avisar la mora al inmueble {}: {}", inmuebleId, e.getMessage());
            }
        }
        log.info("Inmuebles pasados a EN_MORA: {}, avisados: {}", inmueblesEnMora.size(), avisados);
    }
}
