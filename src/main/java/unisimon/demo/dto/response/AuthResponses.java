package unisimon.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas de autenticacion. Los nombres de los tokens siguen el estandar OAuth2 (snake_case). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthResponses {

    public record Sesion(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("token_type") String tokenType,
                         @JsonProperty("expires_in") long expiresIn,
                         @JsonProperty("refresh_token") String refreshToken,
                         UsuarioResponses.Usuario usuario) {
    }
}
