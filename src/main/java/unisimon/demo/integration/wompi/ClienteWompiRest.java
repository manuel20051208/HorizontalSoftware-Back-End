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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public TransaccionWompi consultarTransaccion(String idTransaccion) {
        Map<?, ?> cuerpo = consultar("/transactions/{id}", idTransaccion, "transaccion " + idTransaccion);
        Object data = cuerpo == null ? null : cuerpo.get("data");
        if (!(data instanceof Map<?, ?> tx)) {
            throw new PasarelaNoDisponibleException("La pasarela respondio sin datos de la transaccion");
        }
        return transaccion(tx);
    }

    @Override
    public Optional<TransaccionWompi> buscarPorReferencia(String referencia) {
        Map<?, ?> cuerpo = consultar("/transactions?reference={ref}", referencia, "referencia " + referencia);
        Object data = cuerpo == null ? null : cuerpo.get("data");
        if (!(data instanceof List<?> lista)) {
            throw new PasarelaNoDisponibleException("La pasarela respondio sin la lista de transacciones");
        }
        List<TransaccionWompi> intentos = lista.stream()
                .filter(Map.class::isInstance)
                .map(tx -> transaccion((Map<?, ?>) tx))
                .toList();
        // Un mismo checkout puede tener intentos fallidos y luego uno aprobado: manda el aprobado.
        return intentos.stream().filter(TransaccionWompi::aprobada).findFirst()
                .or(() -> intentos.stream().findFirst());
    }

    private Map<?, ?> consultar(String ruta, String variable, String queSeConsulta) {
        if (PasarelaProperties.vacio(config.privateKey()) || PasarelaProperties.vacio(config.urlApiRest())) {
            throw new PasarelaNoDisponibleException("La llave privada o la URL de la API de la pasarela no estan configuradas");
        }
        try {
            return http.get()
                    .uri(ruta, variable)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.privateKey())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            // Solo el tipo de error: el mensaje podria incluir cabeceras de la peticion.
            log.warn("No se pudo consultar la {} en Wompi: {}", queSeConsulta, e.getClass().getSimpleName());
            throw new PasarelaNoDisponibleException("No se pudo consultar la transaccion en la pasarela");
        }
    }

    private static TransaccionWompi transaccion(Map<?, ?> tx) {
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
