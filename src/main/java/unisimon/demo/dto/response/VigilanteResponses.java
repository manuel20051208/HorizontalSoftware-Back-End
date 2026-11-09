package unisimon.demo.dto.response;

import java.time.LocalTime;
import java.util.List;

/** Respuestas de la porteria. */
public final class VigilanteResponses {

    private VigilanteResponses() {
    }

    public record ZonaPermitida(Long zonaComunId, String nombreZonaComun, LocalTime horaInicio, LocalTime horaFin) {
    }

    /** permitido: la persona puede entrar ahora a alguna zona comun (la reservo ella o su anfitrion). */
    public record Acceso(Long identificacion, String nombreCompleto, Boolean permitido, String motivo,
                         List<ZonaPermitida> zonasPermitidas) {
    }

    /** existe: hay un invitado con esa identificacion; vigente: ademas esta activo y su autorizacion no vencio. */
    public record InvitadoConsulta(Boolean existe, Boolean vigente, InvitadoResponses.Invitado invitado) {
    }

    public record Ingreso(Boolean permitido, String mensaje, String visitante, List<String> inmueblesNotificados) {
    }
}
