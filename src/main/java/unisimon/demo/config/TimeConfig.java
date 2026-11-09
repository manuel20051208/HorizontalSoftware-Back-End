package unisimon.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Reloj unico de la aplicacion (hora de Colombia). Se inyecta donde se necesita "ahora" para poder probarlo. */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
