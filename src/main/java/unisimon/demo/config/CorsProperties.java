package unisimon.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** app.cors.* : origenes del front-end autorizados. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
