package unisimon.demo.mapper;

import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;

import java.time.LocalDateTime;

/** ReservaView -> respuesta. El estado lo decide el dominio ({@link ReservaZonaComun#estado}). */
public final class ReservaMapper {

    private ReservaMapper() {
    }

    public static ReservaResponses.Reserva de(ReservaView v, LocalDateTime ahora) {
        ReservaZonaComun reserva = new ReservaZonaComun(v.getId(), null, null, v.getFechaHoraInicio(),
                v.getFechaHoraFin());
        String estado = reserva.estado(ahora);
        boolean enCurso = ReservaZonaComun.EN_CURSO.equals(estado) && Boolean.TRUE.equals(v.getZonaActiva());
        return new ReservaResponses.Reserva(v.getId(), v.getZonaComunId(), v.getNombreZonaComun(),
                v.getFechaHoraInicio(), v.getFechaHoraFin(), estado, enCurso, reserva.sePuedeCancelar(ahora),
                v.getDuenoReservaId(), v.getNombreDueno(), v.getInmuebleId(), v.getDireccionInmueble());
    }
}
