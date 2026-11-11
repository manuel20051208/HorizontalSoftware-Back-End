package unisimon.demo.dto.response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Respuestas de la porteria. */
public final class VigilanteResponses {

    private VigilanteResponses() {
    }

    /** horaInicio/horaFin: horario de la zona. reservaHasta: cuando termina la reserva que da el acceso. */
    public record ZonaPermitida(Long zonaComunId, String nombreZonaComun, LocalTime horaInicio, LocalTime horaFin,
                                LocalDateTime reservaHasta) {
    }

    /** permitido: la persona tiene ahora una reserva EN CURSO de una zona activa (suya o de su anfitrion). */
    public record Acceso(Long identificacion, String nombreCompleto, Boolean permitido, String motivo,
                         List<ZonaPermitida> zonasPermitidas) {
    }

    /** existe: hay un invitado con esa identificacion; vigente: ademas esta activo y su autorizacion no vencio. */
    public record InvitadoConsulta(Boolean existe, Boolean vigente, InvitadoResponses.Invitado invitado) {
    }

    public record Ingreso(Boolean permitido, String mensaje, String visitante, List<String> inmueblesNotificados) {
    }
}
