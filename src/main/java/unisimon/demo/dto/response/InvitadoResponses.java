package unisimon.demo.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas sobre invitados (usuarios autorizados). vigente = active y autorizacion no vencida. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InvitadoResponses {

    public record Invitado(Long id, Long identificacion, String nombreCompleto, String correoElectronico,
                           Boolean active, LocalDate venceAutorizacion, Boolean vigente) {
    }

    public record Anfitrion(Long id, Long identificacion, String nombreCompleto, Long inmuebleId,
                            String direccionInmueble) {
    }

    public record CambioEstado(Long invitadoId, Boolean active) {
    }

    public record CambioEstadoMasivo(List<CambioEstado> cambios) {
    }
}
