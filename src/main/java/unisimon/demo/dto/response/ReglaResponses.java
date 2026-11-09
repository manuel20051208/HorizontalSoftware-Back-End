package unisimon.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Respuestas sobre reglas de reserva y su asignacion a zonas. */
public final class ReglaResponses {

    private ReglaResponses() {
    }

    public record Rompimiento(Long id, Long zonaComunId, String nombreZonaComun, Long reglaReservaId,
                              LocalDate fechaAplicacion, Boolean activa, String observaciones) {
    }

    public record Regla(Long id, Integer anticipacionMinimaHoras, Integer duracionMaximaHoras,
                        Integer horasAntelacionPenalidad, BigDecimal tarifaPenalidad, Long adminId,
                        List<Rompimiento> zonas) {
    }
}
