package unisimon.demo.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Regla unica que decide el estado de pago de un inmueble (lo que muestra el plano de torres):
 * <ul>
 *   <li>SIN_LIQUIDACION: aun no se le ha generado ninguna liquidacion.</li>
 *   <li>AL_DIA: no debe nada.</li>
 *   <li>VENCIDA: debe y alguna liquidacion esta en mora o paso su fecha limite.</li>
 *   <li>POR_VENCER: debe, pero todavia esta dentro del plazo.</li>
 * </ul>
 */
@Component
public class EstadoPagoPolicy {

    public static final String SIN_LIQUIDACION = "SIN_LIQUIDACION";
    public static final String AL_DIA = "AL_DIA";
    public static final String VENCIDA = "VENCIDA";
    public static final String POR_VENCER = "POR_VENCER";

    private final Clock reloj;

    public EstadoPagoPolicy(Clock reloj) {
        this.reloj = reloj;
    }

    public String calcular(long totalLiquidaciones, BigDecimal saldoPendiente, LocalDate fechaLimitePendiente,
                           boolean tieneMora) {
        if (totalLiquidaciones == 0) {
            return SIN_LIQUIDACION;
        }
        if (saldoPendiente == null || saldoPendiente.signum() == 0) {
            return AL_DIA;
        }
        boolean pasoElPlazo = fechaLimitePendiente != null && LocalDate.now(reloj).isAfter(fechaLimitePendiente);
        return tieneMora || pasoElPlazo ? VENCIDA : POR_VENCER;
    }
}
