package unisimon.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Cuerpos de entrada de /api/auth. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthRequests {

    public record Login(@NotBlank @Email String correo, @NotBlank String contrasena) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    /** codigo: las 4 cifras que llegaron al correo cuando se creo la cuenta (o con /reenviar-codigo). */
    public record Activar(@NotBlank @Email String correo, @NotBlank String contrasena,
                          @NotBlank @Pattern(regexp = "[0-9]{4}", message = "debe tener 4 cifras") String codigo) {
    }

    public record ReenviarCodigo(@NotBlank @Email String correo) {
    }

    public record Recuperar(@NotBlank @Email String correo) {
    }

    public record CambioContrasena(@NotBlank String contrasenaActual, @NotBlank String contrasenaNueva) {
    }
}
