package unisimon.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cuerpos de entrada de la gestion de usuarios por el administrador. */
public final class UsuarioRequests {

    private UsuarioRequests() {
    }

    /**
     * tipoUsuario: ADMINISTRADOR, RESIDENTE o VIGILANTE.
     * inmuebleId es obligatorio para RESIDENTE; inicioContrato y venceContrato para ADMINISTRADOR y VIGILANTE.
     */
    public record CrearUsuario(
            @NotBlank String tipoUsuario,
            @NotNull @Positive Long identificacion,
            @NotBlank @Size(max = 150) String nombreCompleto,
            @Past LocalDate fechaNacimiento,
            @NotBlank @Email String correoElectronico,
            @NotBlank String contrasena,
            Long inmuebleId,
            LocalDate inicioContrato,
            LocalDate venceContrato) {
    }
}
