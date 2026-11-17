package unisimon.demo.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Envia los correos de verdad con la Gmail API (OAuth2 por HTTPS, scope {@code gmail.send}).
 * <p>
 * Se activa solo si {@code GMAIL_REFRESH_TOKEN} tiene valor; si no, queda {@link CorreoLogSender}. El refresh token
 * (obtenido una vez con la cuenta emisora) se cambia por un access token de corta duracion que se guarda en memoria
 * hasta que vence. Es de mejor esfuerzo: si Google falla se registra el motivo y el caso de uso sigue. Nunca registra
 * el cuerpo del correo (puede llevar contrasenas o enlaces) ni los tokens.
 */
@Component
@ConditionalOnExpression("!'${app.mail.gmail.refresh-token:}'.isBlank()")
public class CorreoGmailSender implements CorreoSender {

    private static final Logger log = LoggerFactory.getLogger(CorreoGmailSender.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() { };
    /** Margen para no usar un access token a punto de vencer. */
    private static final Duration MARGEN = Duration.ofSeconds(60);

    private final RestClient http;
    private final Clock reloj;
    private final String urlToken;
    private final String urlEnvio;
    private final String idCliente;
    private final String secretoCliente;
    private final String refreshToken;
    private final String remitente;

    private String accessToken;
    private Instant accessTokenVenceEn = Instant.MIN;

    @Autowired
    public CorreoGmailSender(@Value("${app.mail.gmail.token-url}") String urlToken,
                             @Value("${app.mail.gmail.send-url}") String urlEnvio,
                             @Value("${app.mail.gmail.client-id:}") String idCliente,
                             @Value("${app.mail.gmail.client-secret:}") String secretoCliente,
                             @Value("${app.mail.gmail.refresh-token}") String refreshToken,
                             @Value("${app.mail.remitente:}") String remitente,
                             Clock reloj) {
        this(RestClient.builder().requestFactory(fabrica()), reloj, urlToken, urlEnvio, idCliente, secretoCliente,
                refreshToken, remitente);
    }

    /** Para pruebas: recibe el builder (p. ej. enlazado a un MockRestServiceServer). */
    CorreoGmailSender(RestClient.Builder builder, Clock reloj, String urlToken, String urlEnvio, String idCliente,
                      String secretoCliente, String refreshToken, String remitente) {
        this.http = builder.build();
        this.reloj = reloj;
        this.urlToken = urlToken;
        this.urlEnvio = urlEnvio;
        this.idCliente = limpio(idCliente);
        this.secretoCliente = limpio(secretoCliente);
        this.refreshToken = limpio(refreshToken);
        this.remitente = limpio(remitente);
    }

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        // Para el log: sin saltos de linea, asi nadie puede falsificar entradas del log con el destino o el asunto.
        String para = destino == null ? "" : cabecera(destino);
        String tema = asunto == null ? "" : cabecera(asunto);
        if (para.isEmpty()) {
            log.warn("Correo no enviado: no tiene destinatario. Asunto: {}", tema);
            return;
        }
        if (idCliente.isEmpty() || secretoCliente.isEmpty()) {
            log.warn("Correo no enviado: faltan GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET. Asunto: {}", tema);
            return;
        }
        try {
            String raw = mensajeRaw(remitente, destino, asunto, cuerpo);
            http.post().uri(urlEnvio)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtenerAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", raw))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Correo enviado por Gmail a {} (asunto: {})", para, tema);
        } catch (RestClientResponseException e) {
            // La respuesta de Google explica el rechazo (invalid_grant, scope insuficiente...) y no trae el correo.
            log.error("Google rechazo el correo '{}': HTTP {} {}", tema, e.getStatusCode().value(),
                    cabecera(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("No se pudo enviar el correo '{}' por Gmail: {}", tema, e.getClass().getSimpleName());
        }
    }

    /** Access token vigente; lo renueva con el refresh token cuando falta o esta por vencer. */
    private synchronized String obtenerAccessToken() {
        Instant ahora = reloj.instant();
        if (accessToken != null && ahora.isBefore(accessTokenVenceEn.minus(MARGEN))) {
            return accessToken;
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", idCliente);
        formulario.add("client_secret", secretoCliente);
        formulario.add("refresh_token", refreshToken);
        formulario.add("grant_type", "refresh_token");
        Map<String, Object> respuesta = http.post().uri(urlToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(MAPA);
        if (respuesta == null || !(respuesta.get("access_token") instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Google no devolvio access_token");
        }
        long segundos = respuesta.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        accessToken = token;
        accessTokenVenceEn = ahora.plusSeconds(segundos);
        return token;
    }

    /**
     * Mensaje RFC 2822 en texto plano UTF-8, codificado en base64url como lo pide la Gmail API ({@code raw}).
     * El asunto va en MIME encoded-word y el cuerpo en base64, asi las tildes y la enie llegan bien. Los saltos de
     * linea de las cabeceras se quitan para que nadie pueda inyectar cabeceras extra.
     */
    static String mensajeRaw(String remitente, String destino, String asunto, String cuerpo) {
        StringBuilder m = new StringBuilder();
        if (remitente != null && !remitente.isBlank()) {
            m.append("From: ").append(cabecera(remitente)).append("\r\n");
        }
        m.append("To: ").append(cabecera(destino)).append("\r\n");
        m.append("Subject: =?UTF-8?B?").append(base64(cabecera(asunto == null ? "" : asunto))).append("?=\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(Base64.getMimeEncoder().encodeToString((cuerpo == null ? "" : cuerpo).getBytes(StandardCharsets.UTF_8)));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(m.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String cabecera(String valor) {
        return valor.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String base64(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static SimpleClientHttpRequestFactory fabrica() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(10));
        return f;
    }
}
