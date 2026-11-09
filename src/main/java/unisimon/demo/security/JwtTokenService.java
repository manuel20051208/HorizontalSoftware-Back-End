package unisimon.demo.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import unisimon.demo.exception.NoAutenticadoException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Implementacion de {@link TokenService} con JWT firmados (HS256) de Nimbus. */
@Service
public class JwtTokenService implements TokenService {

    public static final String CLAIM_TIPO = "tipo_token";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_NOMBRE = "nombre";
    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtProperties propiedades;

    public JwtTokenService(JwtEncoder encoder,
                           @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder,
                           JwtProperties propiedades) {
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.propiedades = propiedades;
    }

    @Override
    public ParTokens emitir(IdentidadToken identidad) {
        Instant ahora = Instant.now();
        String access = firmar(identidad, TIPO_ACCESS, ahora, propiedades.accessTtl());
        String refresh = firmar(identidad, TIPO_REFRESH, ahora, propiedades.refreshTtl());
        return new ParTokens(access, refresh, propiedades.accessTtl().toSeconds(), "Bearer");
    }

    @Override
    public IdentidadToken leerRefresh(String refreshToken) {
        try {
            Jwt jwt = refreshDecoder.decode(refreshToken);
            if (!TIPO_REFRESH.equals(jwt.getClaimAsString(CLAIM_TIPO))) {
                throw new NoAutenticadoException("El token no es un refresh token");
            }
            return new IdentidadToken(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(CLAIM_ROL),
                    jwt.getClaimAsString(CLAIM_NOMBRE));
        } catch (JwtException | NumberFormatException e) {
            throw new NoAutenticadoException("Refresh token invalido o expirado");
        }
    }

    @Override
    public String huella(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String firmar(IdentidadToken identidad, String tipo, Instant ahora, Duration vigencia) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.issuer())
                .subject(String.valueOf(identidad.usuarioId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TIPO, tipo)
                .claim(CLAIM_ROL, identidad.rol())
                .claim(CLAIM_NOMBRE, identidad.nombre())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }
}
