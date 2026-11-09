package unisimon.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cuerpos de entrada de /api/auth. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Login(@NotBlank @Email String correo, @NotBlank String contrasena) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    public record Activar(@NotBlank @Email String correo, @NotBlank String contrasena) {
    }

    public record Recuperar(@NotBlank @Email String correo) {
    }

    public record CambioContrasena(@NotBlank String contrasenaActual, @NotBlank String contrasenaNueva) {
    }
}
