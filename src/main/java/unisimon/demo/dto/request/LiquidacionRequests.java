package unisimon.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cuerpos de entrada de liquidaciones. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiquidacionRequests {

    /**
     * periodo: cualquier dia del mes a cobrar (se normaliza al dia 1). fechaLimite por defecto es el dia 10.
     * cuotaAdministracion y valorServiciosComunes por defecto se copian de la liquidacion anterior.
     */
    public record Generar(@NotNull Long inmuebleId,
                          @NotNull LocalDate periodo,
                          LocalDate fechaLimite,
                          @PositiveOrZero BigDecimal cuotaAdministracion,
                          @PositiveOrZero BigDecimal valorServiciosComunes) {
    }

    public record GenerarMasiva(@NotNull LocalDate periodo, LocalDate fechaLimite) {
    }
}
