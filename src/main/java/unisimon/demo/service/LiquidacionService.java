package unisimon.demo.service;

import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.dto.response.Pagina;

import java.time.LocalDate;
import java.util.List;

/** Ciclo de vida de las liquidaciones mensuales. */
public interface LiquidacionService {

    /** Liquidacion.generarLiquidacion(inmueble): crea la liquidacion de un periodo y la deja como vigente. */
    LiquidacionResponses.Liquidacion generarLiquidacion(LiquidacionRequests.Generar solicitud);

    /** Genera el periodo para todos los inmuebles activos copiando sus tarifas anteriores. */
    LiquidacionResponses.ResultadoMasiva generarMasiva(LiquidacionRequests.GenerarMasiva solicitud);

    /** Administrador.configurarTarifas(inmueble, adminId): ajusta cuota/servicios de la liquidacion PENDIENTE vigente. */
    LiquidacionResponses.Liquidacion configurarTarifas(Long inmuebleId, InmuebleRequests.Tarifas tarifas);

    /** Administrador.configurarMultas(inmueble, adminId): fija las multas de la liquidacion PENDIENTE vigente. */
    LiquidacionResponses.Liquidacion configurarMultas(Long inmuebleId, InmuebleRequests.Multas multas);

    /** Liquidacion.marcarEnMora() */
    LiquidacionResponses.Liquidacion marcarEnMora(Long liquidacionId);

    /** Liquidacion.marcarAlDia() */
    LiquidacionResponses.Liquidacion marcarAlDia(Long liquidacionId);

    Pagina<LiquidacionResponses.Liquidacion> listar(Long inmuebleId, String estado, LocalDate periodo,
                                                  SolicitudPagina pagina);

    /**
     * Pasa a EN_MORA las liquidaciones PENDIENTES cuya fecha limite ya paso. Devuelve los ids de los inmuebles que
     * quedaron en mora (sin repetir), para avisarles.
     */
    List<Long> marcarVencidas();

    /** Un pago aprobado deja al dia todas las liquidaciones que el inmueble debia. */
    void marcarInmueblePagado(Long inmuebleId);

    /** Un reembolso reabre la liquidacion vigente del inmueble (PENDIENTE, o EN_MORA si ya vencio). */
    void reabrirVigente(Long inmuebleId);

    LiquidacionResponses.Liquidacion consultar(Long liquidacionId);

    /** Fecha limite y valores de una liquidacion que aun no esta pagada; el total lo recalcula el dominio. */
    LiquidacionResponses.Liquidacion actualizar(Long liquidacionId, LiquidacionRequests.Actualizar solicitud);

    /** Borra una liquidacion no pagada; si era la vigente, la vigente pasa a ser la mas reciente que quede. */
    void eliminar(Long liquidacionId);
}
