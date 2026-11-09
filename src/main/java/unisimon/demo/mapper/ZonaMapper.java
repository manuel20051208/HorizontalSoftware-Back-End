package unisimon.demo.mapper;

import unisimon.demo.dto.response.ReglaResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.repository.ReglaReservaRepository.ReglaView;
import unisimon.demo.repository.RompimientoRepository.RompimientoView;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;

import java.util.List;

/** Proyecciones de zonas, reglas y rompimientos -> respuestas del API. */
public final class ZonaMapper {

    private ZonaMapper() {
    }

    public static ZonaResponses.Zona zona(ZonaView v) {
        return new ZonaResponses.Zona(v.getId(), v.getNombreZonaComun(), v.getCapacidad(), v.getHoraInicio(),
                v.getHoraFin(), v.getIsActive(), v.getReservasActuales());
    }

    public static ReglaResponses.Rompimiento rompimiento(RompimientoView v) {
        return new ReglaResponses.Rompimiento(v.getId(), v.getZonaComunId(), v.getNombreZonaComun(),
                v.getReglaReservaId(), v.getFechaAplicacion(), v.getActiva(), v.getObservaciones());
    }

    public static ReglaResponses.Regla regla(ReglaView v, List<RompimientoView> zonas) {
        return new ReglaResponses.Regla(v.getId(), v.getAnticipacionMinimaHoras(), v.getDuracionMaximaHoras(),
                v.getHorasAntelacionPenalidad(), v.getTarifaPenalidad(), v.getAdminId(),
                zonas.stream().map(ZonaMapper::rompimiento).toList());
    }
}
