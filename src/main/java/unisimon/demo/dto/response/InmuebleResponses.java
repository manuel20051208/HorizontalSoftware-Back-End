package unisimon.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuestas sobre inmuebles. estadoPago: AL_DIA, POR_VENCER, VENCIDA o SIN_LIQUIDACION.
 * saldoPendiente = suma de todas las liquidaciones que el inmueble aun no ha pagado.
 */
public final class InmuebleResponses {

    private InmuebleResponses() {
    }

    public record Resumen(Long id, String direccionInmueble, Boolean isActive, String estadoPago,
                          BigDecimal saldoPendiente, LocalDate fechaLimitePendiente, BigDecimal valorParqueadero,
                          Long totalLiquidaciones) {
    }

    public record Detalle(Long id, String direccionInmueble, Boolean isActive, String estadoPago,
                          BigDecimal saldoPendiente, LocalDate fechaLimitePendiente, BigDecimal valorParqueadero,
                          List<String> nombresUsuarios, List<UsuarioResponses.Usuario> usuarios,
                          List<ZonaResponses.Zona> zonasComunes, LiquidacionResponses.Liquidacion liquidacionVigente) {
    }

    /** Lo que ve el residente en "Estado de cuenta". pazYSalvo = sin saldo pendiente y liquidacion vigente al dia. */
    public record EstadoCuenta(Long inmuebleId, String direccionInmueble, String estadoPago,
                               BigDecimal saldoPendiente, Boolean pazYSalvo,
                               LiquidacionResponses.Liquidacion liquidacionVigente) {
    }

    /** Totales del panel del administrador. */
    public record ResumenAdmin(LocalDate periodo, int totalInmuebles, int alDia, int porVencer, int vencidas,
                               int sinLiquidacion, BigDecimal porCobrar, BigDecimal recaudoMes) {
    }
}
