package unisimon.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Cuerpos de entrada de invitados. */
public final class InvitadoRequests {

    private InvitadoRequests() {
    }

    /**
     * venceAutorizacion: ultimo dia que puede entrar; sin fecha = autorizacion permanente.
     * correoElectronico es opcional (se genera uno interno si no se envia).
     */
    public record CrearInvitado(@NotNull @Positive Long identificacion,
                                @NotBlank @Size(max = 150) String nombreCompleto,
                                @Past LocalDate fechaNacimiento,
                                @Email String correoElectronico,
                                LocalDate venceAutorizacion) {
    }

    public record CambiarEstadoInvitados(@NotEmpty List<@NotNull Long> invitadoIds) {
    }
}
