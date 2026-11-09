package unisimon.demo.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.seed.* : datos iniciales de desarrollo. */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean enabled, Admin admin) {

    public record Admin(Long identificacion, String nombre, String correo, String contrasena) {
    }
}
