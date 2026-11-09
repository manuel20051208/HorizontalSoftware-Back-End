package unisimon.demo.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.pasarela.* : configuracion inicial de la pasarela de pagos. */
@ConfigurationProperties(prefix = "app.pasarela")
public record PasarelaProperties(String nombre, String urlApi, String apiKey) {
}
