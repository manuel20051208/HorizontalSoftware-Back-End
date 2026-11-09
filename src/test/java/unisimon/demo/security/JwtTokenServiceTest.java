package unisimon.demo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import unisimon.demo.config.JwtConfig;
import unisimon.demo.exception.NoAutenticadoException;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el flujo real de tokens (Nimbus + validadores), sin mocks: es la base de OAuth2 y del refresh token. */
class JwtTokenServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres!!";

    private JwtProperties propiedades;
    private JwtDecoder decodificadorAccess;
    private JwtTokenService servicio;
    private final IdentidadToken identidad = new IdentidadToken(7L, "RESIDENTE", "Santiago Mendez");

    @BeforeEach
    void preparar() {
        crear(Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private void crear(Duration access, Duration refresh) {
        propiedades = new JwtProperties("torres-del-parque", SECRETO, access, refresh);
        JwtConfig config = new JwtConfig();
        SecretKey llave = config.jwtSecretKey(propiedades);
        decodificadorAccess = config.jwtDecoder(llave, propiedades);
        servicio = new JwtTokenService(config.jwtEncoder(llave), config.refreshJwtDecoder(llave, propiedades),
                propiedades);
    }

    @Test
    @DisplayName("emite un par de tokens estilo OAuth2 (Bearer, expires_in en segundos)")
    void emitir() {
        ParTokens par = servicio.emitir(identidad);

        assertThat(par.tokenType()).isEqualTo("Bearer");
        assertThat(par.expiresIn()).isEqualTo(900);
        assertThat(par.accessToken()).isNotBlank().isNotEqualTo(par.refreshToken());
    }

    @Test
    @DisplayName("el access token lleva sub, rol y nombre, y lo acepta el decodificador del servidor de recursos")
    void accessToken() {
        Jwt jwt = decodificadorAccess.decode(servicio.emitir(identidad).accessToken());

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_ROL)).isEqualTo("RESIDENTE");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_NOMBRE)).isEqualTo("Santiago Mendez");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_TIPO)).isEqualTo(JwtTokenService.TIPO_ACCESS);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("torres-del-parque");
    }

    @Test
    @DisplayName("un refresh token NO sirve como access token")
    void refreshNoEsAccess() {
        String refresh = servicio.emitir(identidad).refreshToken();

        assertThatThrownBy(() -> decodificadorAccess.decode(refresh)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("leerRefresh devuelve la identidad del refresh token")
    void leerRefresh() {
        IdentidadToken leida = servicio.leerRefresh(servicio.emitir(identidad).refreshToken());

        assertThat(leida).isEqualTo(identidad);
    }

    @Test
    @DisplayName("leerRefresh rechaza un access token, un token alterado y basura")
    void leerRefreshRechaza() {
        ParTokens par = servicio.emitir(identidad);

        assertThatThrownBy(() -> servicio.leerRefresh(par.accessToken())).isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.leerRefresh(par.refreshToken() + "x")).isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.leerRefresh("no-es-un-jwt")).isInstanceOf(NoAutenticadoException.class);
    }

    @Test
    @DisplayName("un refresh token vencido se rechaza")
    void refreshVencido() {
        JwtConfig config = new JwtConfig();
        SecretKey llave = config.jwtSecretKey(propiedades);
        Instant hace2Horas = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.issuer()).subject("7")
                .issuedAt(hace2Horas).expiresAt(hace2Horas.plus(Duration.ofHours(1)))
                .claim(JwtTokenService.CLAIM_TIPO, JwtTokenService.TIPO_REFRESH)
                .claim(JwtTokenService.CLAIM_ROL, "RESIDENTE")
                .build();
        String vencido = config.jwtEncoder(llave)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> servicio.leerRefresh(vencido)).isInstanceOf(NoAutenticadoException.class);
    }

    @Test
    @DisplayName("un token firmado con otra llave se rechaza")
    void otraLlave() {
        String refresh = servicio.emitir(identidad).refreshToken();
        JwtProperties otras = new JwtProperties("torres-del-parque", "otra-llave-completamente-distinta-0123456789",
                Duration.ofMinutes(15), Duration.ofDays(7));
        JwtConfig config = new JwtConfig();
        JwtTokenService ajeno = new JwtTokenService(config.jwtEncoder(config.jwtSecretKey(otras)),
                config.refreshJwtDecoder(config.jwtSecretKey(otras), otras), otras);

        assertThatThrownBy(() -> ajeno.leerRefresh(refresh)).isInstanceOf(NoAutenticadoException.class);
    }

    @Test
    @DisplayName("la huella es SHA-256 en hexadecimal, estable y distinta por token")
    void huella() {
        String a = servicio.huella("token-a");

        assertThat(a).hasSize(64).matches("[0-9a-f]+");
        assertThat(servicio.huella("token-a")).isEqualTo(a);
        assertThat(servicio.huella("token-b")).isNotEqualTo(a);
    }

    @Test
    @DisplayName("la configuracion exige un secreto de al menos 32 caracteres")
    void secretoCorto() {
        JwtProperties corto = new JwtProperties("x", "corto", Duration.ofMinutes(1), Duration.ofMinutes(2));

        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(corto))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
    }
}
