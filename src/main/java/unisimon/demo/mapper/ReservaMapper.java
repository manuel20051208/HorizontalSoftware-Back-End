package unisimon.demo.mapper;

import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;

/** ReservaView -> respuesta. */
public final class ReservaMapper {

    private ReservaMapper() {
    }

    public static ReservaResponses.Reserva de(ReservaView v, boolean enCurso) {
        return new ReservaResponses.Reserva(v.getId(), v.getZonaComunId(), v.getNombreZonaComun(), v.getHoraInicio(),
                v.getHoraFin(), enCurso, v.getDuenoReservaId(), v.getNombreDueno(), v.getInmuebleId(),
                v.getDireccionInmueble());
    }
}
