package unisimon.demo.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Al arrancar avisa que secretos de la pasarela faltan (solo los nombres, nunca los valores).
 * No detiene la aplicacion: el resto del sistema funciona y los pagos responden 409 hasta configurarlos.
 */
@Component
public class AvisoConfiguracionPasarela {

    private static final Logger log = LoggerFactory.getLogger(AvisoConfiguracionPasarela.class);

    private final PasarelaProperties config;

    public AvisoConfiguracionPasarela(PasarelaProperties config) {
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        List<String> faltan = config.secretosFaltantes();
        if (!faltan.isEmpty()) {
            log.warn("Pasarela de pagos sin configurar por completo; faltan las variables: {}. "
                    + "Los pagos en linea y el webhook no funcionaran hasta definirlas.", String.join(", ", faltan));
        }
    }
}
