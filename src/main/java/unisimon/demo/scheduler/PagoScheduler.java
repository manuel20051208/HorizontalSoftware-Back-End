package unisimon.demo.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unisimon.demo.service.PasarelaPagoService;

/**
 * Respaldo del webhook de Wompi: cada 5 minutos revisa en Wompi los pagos en linea que siguen PENDIENTES.
 * Cubre los avisos que nunca llegan (URL de eventos mal configurada, API caida) y anula los checkouts abandonados,
 * que de otro modo quedarian PENDIENTES para siempre.
 */
@Component
@RequiredArgsConstructor
public class PagoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PagoScheduler.class);

    private final PasarelaPagoService pasarela;

    @Scheduled(cron = "0 */5 * * * *", zone = "America/Bogota")
    public void sincronizarPagosPendientes() {
        int actualizados = pasarela.sincronizarPendientes();
        if (actualizados > 0) {
            log.info("Pagos pendientes actualizados desde la pasarela: {}", actualizados);
        }
    }
}
