package unisimon.demo.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import unisimon.demo.security.JwtProperties;
import unisimon.demo.security.JwtTokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Beans de OAuth2/JWT: la llave HMAC, el codificador y los dos decodificadores.
 * El decodificador principal (access) lo usa el servidor de recursos de Spring Security.
 */
@Configuration
public class JwtConfig {

    private static final int MINIMO_BYTES_SECRETO = 32;

    @Bean
    public SecretKey jwtSecretKey(JwtProperties propiedades) {
        byte[] bytes = propiedades.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMO_BYTES_SECRETO) {
            throw new IllegalStateException("app.jwt.secret debe tener al menos 32 caracteres");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Valida los access tokens de cada peticion (firma, emisor, vencimiento y tipo = access). */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties propiedades) {
        return construir(jwtSecretKey, propiedades, JwtTokenService.TIPO_ACCESS);
    }

    /** Valida los refresh tokens que llegan a /api/auth/refresh. */
    @Bean("refreshJwtDecoder")
    public JwtDecoder refreshJwtDecoder(SecretKey jwtSecretKey, JwtProperties propiedades) {
        return construir(jwtSecretKey, propiedades, JwtTokenService.TIPO_REFRESH);
    }

    private static JwtDecoder construir(SecretKey llave, JwtProperties propiedades, String tipoEsperado) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llave).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validadorTipo = jwt -> tipoEsperado.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TIPO))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Tipo de token incorrecto", null));
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.issuer()), validadorTipo));
        return decodificador;
    }
}
