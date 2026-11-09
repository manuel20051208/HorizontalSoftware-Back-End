package unisimon.demo.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuracion de los tokens (app.jwt.*). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTtl, Duration refreshTtl) {
}
