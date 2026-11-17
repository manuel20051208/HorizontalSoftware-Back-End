package unisimon.demo.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorreoGmailSenderTest {

    private static final String URL_TOKEN = "https://oauth2.example.test/token";
    private static final String URL_ENVIO = "https://gmail.example.test/send";
    private static final String TOKEN_OK = "{\"access_token\":\"tok\",\"expires_in\":3600}";
    private static final Pattern RAW = Pattern.compile("\"raw\"\\s*:\\s*\"([A-Za-z0-9_-]+)\"");

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;
    private RelojMutable reloj;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        reloj = new RelojMutable(Instant.parse("2026-11-17T15:00:00Z"));
    }

    private CorreoGmailSender emisor(String idCliente, String secreto, String remitente) {
        return new CorreoGmailSender(builder, reloj, URL_TOKEN, URL_ENVIO, idCliente, secreto, "refresh-1", remitente);
    }

    private CorreoGmailSender emisor() {
        return emisor("id-cliente", "secreto", "Conjunto <conjunto@mail.com>");
    }

    private void esperarToken() {
        servidor.expect(once(), requestTo(URL_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
    }

    private void esperarEnvio(StringBuilder capturado) {
        servidor.expect(once(), requestTo(URL_ENVIO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(capturar(capturado))
                .andRespond(withSuccess());
    }

    private static RequestMatcher capturar(StringBuilder destino) {
        return solicitud -> destino.append(((MockClientHttpRequest) solicitud).getBodyAsString(StandardCharsets.UTF_8));
    }

    /** Extrae el campo raw del JSON enviado y lo decodifica desde base64url. */
    private static String decodificarRaw(String json) {
        Matcher m = RAW.matcher(json);
        assertThat(m.find()).as("el cuerpo debe traer el campo raw: %s", json).isTrue();
        return new String(Base64.getUrlDecoder().decode(m.group(1)), StandardCharsets.UTF_8);
    }

    private static List<String> cabeceras(String mensaje) {
        return Arrays.asList(mensaje.substring(0, mensaje.indexOf("\r\n\r\n")).split("\r\n"));
    }

    private static String cuerpo(String mensaje) {
        String b64 = mensaje.substring(mensaje.indexOf("\r\n\r\n") + 4);
        return new String(Base64.getMimeDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    private static String asuntoDecodificado(List<String> cabeceras) {
        String subject = cabeceras.stream().filter(c -> c.startsWith("Subject: ")).findFirst().orElseThrow();
        assertThat(subject).startsWith("Subject: =?UTF-8?B?").endsWith("?=");
        String b64 = subject.substring("Subject: =?UTF-8?B?".length(), subject.length() - 2);
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("enviar pide el access token con el refresh token y manda el mensaje con Bearer y raw base64url")
    void envioFeliz() {
        servidor.expect(once(), requestTo(URL_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "id-cliente",
                        "client_secret", "secreto",
                        "refresh_token", "refresh-1",
                        "grant_type", "refresh_token")))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        StringBuilder json = new StringBuilder();
        servidor.expect(once(), requestTo(URL_ENVIO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(capturar(json))
                .andRespond(withSuccess());

        String asunto = "Recuperación de contraseña ñ";
        String texto = "Hola Señor Pérez,\nsu contraseña temporal es: Áé1-ñ\n¡Gracias!";
        emisor().enviar("destino@mail.com", asunto, texto);

        servidor.verify();
        String mensaje = decodificarRaw(json.toString());
        List<String> cab = cabeceras(mensaje);
        assertThat(cab).contains("To: destino@mail.com", "From: Conjunto <conjunto@mail.com>",
                "MIME-Version: 1.0", "Content-Type: text/plain; charset=UTF-8", "Content-Transfer-Encoding: base64");
        assertThat(asuntoDecodificado(cab)).isEqualTo(asunto);
        assertThat(cuerpo(mensaje)).isEqualTo(texto);
    }

    @Test
    @DisplayName("el access token se reutiliza mientras esta vigente y se renueva al acercarse su vencimiento")
    void cacheDelToken() {
        esperarToken();
        esperarEnvio(new StringBuilder());
        esperarEnvio(new StringBuilder());
        CorreoGmailSender emisor = emisor();

        emisor.enviar("a@mail.com", "Uno", "x");
        reloj.avanzar(Duration.ofSeconds(3600 - 61));
        emisor.enviar("a@mail.com", "Dos", "x");
        servidor.verify();

        servidor.reset();
        esperarToken();
        esperarEnvio(new StringBuilder());
        reloj.avanzar(Duration.ofSeconds(2));
        emisor.enviar("a@mail.com", "Tres", "x");
        servidor.verify();
    }

    @Test
    @DisplayName("si Google rechaza el refresh token (400 invalid_grant) enviar no lanza excepcion")
    void tokenRechazado() {
        servidor.expect(once(), requestTo(URL_TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertThatCode(() -> emisor().enviar("a@mail.com", "Asunto", "x")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("si el envio responde 500 enviar no lanza excepcion")
    void envioFallido() {
        esperarToken();
        servidor.expect(once(), requestTo(URL_ENVIO)).andRespond(withServerError());

        assertThatCode(() -> emisor().enviar("a@mail.com", "Asunto", "x")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("si la respuesta del token no trae access_token enviar no lanza excepcion ni envia")
    void tokenSinAccessToken() {
        servidor.expect(once(), requestTo(URL_TOKEN))
                .andRespond(withSuccess("{\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> emisor().enviar("a@mail.com", "Asunto", "x")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("sin destinatario (null o en blanco) no hace ninguna llamada HTTP")
    void sinDestinatario() {
        CorreoGmailSender emisor = emisor();

        emisor.enviar(null, "Asunto", "x");
        emisor.enviar("   ", "Asunto", "x");

        servidor.verify();
    }

    @Test
    @DisplayName("sin client id o sin secreto no hace ninguna llamada HTTP")
    void sinCredencialesCliente() {
        emisor("", "secreto", "r@mail.com").enviar("a@mail.com", "Asunto", "x");
        emisor("id", null, "r@mail.com").enviar("a@mail.com", "Asunto", "x");
        emisor("   ", "  ", "r@mail.com").enviar("a@mail.com", "Asunto", "x");

        servidor.verify();
    }

    @Test
    @DisplayName("un asunto o destino con saltos de linea no inyecta una cabecera Bcc")
    void sinInyeccionDeCabeceras() {
        esperarToken();
        StringBuilder json = new StringBuilder();
        esperarEnvio(json);

        emisor().enviar("a@mail.com\r\nBcc: x@y.com", "Hola\r\nBcc: x@y.com", "x");

        servidor.verify();
        List<String> cab = cabeceras(decodificarRaw(json.toString()));
        assertThat(cab).noneMatch(c -> c.regionMatches(true, 0, "Bcc", 0, 3));
        assertThat(cab).contains("To: a@mail.com Bcc: x@y.com");
        assertThat(asuntoDecodificado(cab)).isEqualTo("Hola Bcc: x@y.com");

        String directo = new String(Base64.getUrlDecoder().decode(
                CorreoGmailSender.mensajeRaw("r@mail.com\nBcc: z@y.com", "a@mail.com\rBcc: x@y.com", "s", "c")),
                StandardCharsets.UTF_8);
        assertThat(cabeceras(directo)).noneMatch(c -> c.regionMatches(true, 0, "Bcc", 0, 3));
    }

    @Test
    @DisplayName("sin remitente el mensaje no lleva cabecera From")
    void sinRemitente() {
        esperarToken();
        StringBuilder json = new StringBuilder();
        esperarEnvio(json);

        emisor("id-cliente", "secreto", "   ").enviar("a@mail.com", "Asunto", "x");

        servidor.verify();
        List<String> cab = cabeceras(decodificarRaw(json.toString()));
        assertThat(cab).noneMatch(c -> c.startsWith("From:"));
        assertThat(cab).contains("To: a@mail.com");

        String directo = new String(Base64.getUrlDecoder().decode(
                CorreoGmailSender.mensajeRaw(null, "a@mail.com", null, null)), StandardCharsets.UTF_8);
        assertThat(cabeceras(directo)).noneMatch(c -> c.startsWith("From:"));
        assertThat(asuntoDecodificado(cabeceras(directo))).isEmpty();
        assertThat(cuerpo(directo)).isEmpty();
    }

    @Test
    @DisplayName("las credenciales y el remitente con espacios alrededor se envian recortados")
    void valoresRecortados() {
        servidor.expect(once(), requestTo(URL_TOKEN))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "id-cliente",
                        "client_secret", "secreto",
                        "refresh_token", "refresh-1")))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        StringBuilder json = new StringBuilder();
        esperarEnvio(json);

        new CorreoGmailSender(builder, reloj, URL_TOKEN, URL_ENVIO, "  id-cliente  ", "\tsecreto ",
                "  refresh-1\n", "  r@mail.com  ").enviar("  a@mail.com  ", "Asunto", "x");

        servidor.verify();
        List<String> cab = cabeceras(decodificarRaw(json.toString()));
        assertThat(cab).contains("From: r@mail.com", "To: a@mail.com");
    }

    /** Reloj que se puede adelantar a mano para probar el vencimiento del token. */
    private static final class RelojMutable extends Clock {
        private Instant ahora;

        RelojMutable(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
