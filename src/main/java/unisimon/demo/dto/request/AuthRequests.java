package unisimon.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Cuerpos de entrada de /api/auth. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthRequests {

    public record Login(@NotBlank @Email String correo, @NotBlank String contrasena) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    public record Recuperar(@NotBlank @Email String correo) {
    }

    public record CambioContrasena(@NotBlank String contrasenaActual, @NotBlank String contrasenaNueva) {
    }
}
