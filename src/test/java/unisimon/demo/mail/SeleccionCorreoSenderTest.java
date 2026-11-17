package unisimon.demo.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Spring deja activo exactamente un CorreoSender: Gmail si hay refresh token, el log si no. */
class SeleccionCorreoSenderTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(CorreoLogSender.class, CorreoGmailSender.class)
            .withPropertyValues(
                    "app.mail.gmail.token-url=https://oauth2.googleapis.com/token",
                    "app.mail.gmail.send-url=https://gmail.googleapis.com/gmail/v1/users/me/messages/send");

    @Test
    void sinRefreshTokenUsaElLog() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(CorreoSender.class);
            assertThat(ctx.getBean(CorreoSender.class)).isInstanceOf(CorreoLogSender.class);
        });
    }

    @Test
    void conRefreshTokenEnBlancoUsaElLog() {
        contexto.withPropertyValues("app.mail.gmail.refresh-token=   ").run(ctx -> {
            assertThat(ctx).hasSingleBean(CorreoSender.class);
            assertThat(ctx.getBean(CorreoSender.class)).isInstanceOf(CorreoLogSender.class);
        });
    }

    @Test
    void conRefreshTokenUsaGmail() {
        contexto.withPropertyValues(
                "app.mail.gmail.refresh-token=1//token-de-prueba",
                "app.mail.gmail.client-id=id",
                "app.mail.gmail.client-secret=secreto",
                "app.mail.remitente=remitente@gmail.com").run(ctx -> {
            assertThat(ctx).hasSingleBean(CorreoSender.class);
            assertThat(ctx.getBean(CorreoSender.class)).isInstanceOf(CorreoGmailSender.class);
        });
    }
}
