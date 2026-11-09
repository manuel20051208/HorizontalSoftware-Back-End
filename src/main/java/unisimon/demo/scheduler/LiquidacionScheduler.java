package unisimon.demo.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unisimon.demo.service.LiquidacionService;

/** Tarea programada: cada madrugada pasa a EN_MORA las liquidaciones que vencieron sin pagarse. */
@Component
public class LiquidacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionScheduler.class);

    private final LiquidacionService liquidaciones;

    public LiquidacionScheduler(LiquidacionService liquidaciones) {
        this.liquidaciones = liquidaciones;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Bogota")
    public void marcarVencidas() {
        int cambiadas = liquidaciones.marcarVencidas();
        log.info("Liquidaciones pasadas a EN_MORA: {}", cambiadas);
    }
}
