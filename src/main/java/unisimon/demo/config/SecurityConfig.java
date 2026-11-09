package unisimon.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import unisimon.demo.security.RolJwtConverter;
import unisimon.demo.security.SecurityErrorHandlers;

/**
 * Cadena de seguridad: servidor de recursos OAuth2 con JWT (Bearer), sin sesiones y sin CSRF.
 * <p>
 * NO hay reglas por URL: el filtro solo autentica el token. Quien puede entrar a cada endpoint se
 * declara con etiquetas en los controladores (@Publico, @Autenticado, @SoloAdministrador...) y lo aplica
 * la seguridad por metodo (@EnableMethodSecurity). Una prueba unitaria garantiza que ningun endpoint quede sin etiqueta.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RolJwtConverter convertidor,
                                           SecurityErrorHandlers errores) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }
}
