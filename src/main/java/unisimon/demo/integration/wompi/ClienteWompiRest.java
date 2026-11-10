package unisimon.demo.integration.wompi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import unisimon.demo.bootstrap.PasarelaProperties;
import unisimon.demo.exception.PasarelaNoDisponibleException;

import java.time.Duration;
import java.util.Map;

/** Cliente HTTP de la API REST de Wompi. Autentica con la llave privada (Bearer), que nunca se registra en logs. */
@Component
public class ClienteWompiRest implements ClienteWompi {

    private static final Logger log = LoggerFactory.getLogger(ClienteWompiRest.class);

    private final RestClient http;
    private final PasarelaProperties config;

    public ClienteWompiRest(PasarelaProperties config) {
        this.config = config;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        String base = PasarelaProperties.vacio(config.urlApiRest()) ? "" : config.urlApiRest().replaceAll("/+$", "");
        this.http = RestClient.builder().baseUrl(base).requestFactory(fabrica).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public TransaccionWompi consultarTransaccion(String idTransaccion) {
        if (PasarelaProperties.vacio(config.privateKey()) || PasarelaProperties.vacio(config.urlApiRest())) {
            throw new PasarelaNoDisponibleException("La llave privada o la URL de la API de la pasarela no estan configuradas");
        }
        Map<String, Object> cuerpo;
        try {
            cuerpo = http.get()
                    .uri("/transactions/{id}", idTransaccion)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.privateKey())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            // Solo el tipo de error: el mensaje podria incluir cabeceras de la peticion.
            log.warn("No se pudo consultar la transaccion {} en Wompi: {}", idTransaccion, e.getClass().getSimpleName());
            throw new PasarelaNoDisponibleException("No se pudo consultar la transaccion en la pasarela");
        }
        Object data = cuerpo == null ? null : cuerpo.get("data");
        if (!(data instanceof Map<?, ?> tx)) {
            throw new PasarelaNoDisponibleException("La pasarela respondio sin datos de la transaccion");
        }
        Object monto = tx.get("amount_in_cents");
        if (!(monto instanceof Number centavos)) {
            throw new PasarelaNoDisponibleException("La pasarela respondio una transaccion sin monto");
        }
        return new TransaccionWompi(texto(tx.get("id")), texto(tx.get("reference")), texto(tx.get("status")),
                centavos.longValue(), texto(tx.get("currency")));
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
