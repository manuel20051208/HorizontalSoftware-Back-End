package unisimon.demo.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacion de desarrollo: en vez de enviar el correo lo escribe en el log del servidor.
 * Para produccion basta con crear otra implementacion de {@link CorreoSender} (SMTP, SendGrid...).
 */
@Component
public class CorreoLogSender implements CorreoSender {

    private static final Logger log = LoggerFactory.getLogger(CorreoLogSender.class);

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        log.info("[CORREO SIMULADO] para={} asunto='{}' cuerpo='{}'", destino, asunto, cuerpo);
    }
}
