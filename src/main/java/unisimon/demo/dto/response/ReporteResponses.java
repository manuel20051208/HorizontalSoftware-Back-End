package unisimon.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas de los reportes del administrador (Administrador.generarReportes). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReporteResponses {

    /**
     * Una fila del reporte. estadoPago: AL_DIA, POR_VENCER, VENCIDA o SIN_LIQUIDACION.
     * recaudado = suma de sus pagos aprobados; saldoPendiente = lo que aun debe.
     */
    public record Inmueble(Long inmuebleId, String direccionInmueble, String estadoPago, BigDecimal recaudado,
                           BigDecimal saldoPendiente) {
    }

    /**
     * Reporte consolidado: recaudo (pagos aprobados), mora (saldo de los inmuebles vencidos) y pendiente
     * (saldo de los que aun estan dentro del plazo), con el detalle de cada inmueble.
     */
    public record Consolidado(LocalDate fechaGeneracion, int totalInmuebles, int inmueblesAlDia, int inmueblesEnMora,
                              int inmueblesPendientes, BigDecimal recaudo, BigDecimal mora, BigDecimal pendiente,
                              List<Inmueble> inmuebles) {
    }
}
