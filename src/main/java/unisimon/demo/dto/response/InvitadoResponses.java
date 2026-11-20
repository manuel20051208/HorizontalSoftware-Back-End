package unisimon.demo.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas sobre invitados (usuarios autorizados). vigente = active y autorizacion no vencida. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InvitadoResponses {

    public record Invitado(Long id, Long identificacion, String nombreCompleto, String correoElectronico,
                           Boolean active, LocalDate venceAutorizacion, Boolean vigente,
                           List<UsuarioResponses.InmuebleResumen> inmuebles) {
    }

    public record Anfitrion(Long id, Long identificacion, String nombreCompleto,
                            List<UsuarioResponses.InmuebleResumen> inmuebles) {
    }

    public record CambioEstado(Long invitadoId, Boolean active) {
    }

    public record CambioEstadoMasivo(List<CambioEstado> cambios) {
    }
}
