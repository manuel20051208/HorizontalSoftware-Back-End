package unisimon.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Respuestas sobre liquidaciones. */
public final class LiquidacionResponses {

    private LiquidacionResponses() {
    }

    public record Liquidacion(Long id, Long inmuebleId, String direccionInmueble, LocalDate periodo,
                              LocalDate fechaGeneracion, LocalDate fechaLimite, BigDecimal cuotaAdministracion,
                              BigDecimal valorServiciosComunes, BigDecimal valorMultas, BigDecimal total,
                              String estado, Boolean vencida) {
    }

    public record Omitida(Long inmuebleId, String motivo) {
    }

    public record ResultadoMasiva(int generadas, List<Omitida> omitidas) {
    }
}
