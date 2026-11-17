package unisimon.demo.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Implementacion de desarrollo: en vez de enviar el correo lo escribe en el log del servidor.
 * Se usa cuando no hay {@code GMAIL_REFRESH_TOKEN}; si lo hay, envia {@link CorreoGmailSender}.
 */
@Component
@ConditionalOnExpression("'${app.mail.gmail.refresh-token:}'.isBlank()")
public class CorreoLogSender implements CorreoSender {

    private static final Logger log = LoggerFactory.getLogger(CorreoLogSender.class);

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        log.info("[CORREO SIMULADO] para={} asunto='{}' cuerpo='{}'", destino, asunto, cuerpo);
    }
}
