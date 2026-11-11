package unisimon.demo.service;

import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.security.UsuarioAutenticado;

/** Registro y estados de los pagos de administracion. */
public interface PagoService {

    /** Pago.registrarPago(inmueble): el residente inicia el pago de su saldo y recibe la URL de la pasarela. */
    PagoResponses.IniciarPago registrarPago(Long residenteId, PagoRequests.IniciarPago solicitud);

    /** Administrador.conciliarPagosAdministracion(inmueble, adminId): registra un pago manual ya recibido. */
    PagoResponses.Pago conciliarPagosAdministracion(Long inmuebleId, PagoRequests.Conciliar solicitud);

    /** Pago.actualizarEstado(pago): marca el pago como aprobado y deja al dia las liquidaciones del inmueble. */
    PagoResponses.Pago aprobarPago(Long pagoId);

    /** La pasarela informo que el pago fue rechazado. */
    PagoResponses.Pago rechazarPago(Long pagoId);

    /** Pago.anularPago(): solo pagos PENDIENTES. */
    PagoResponses.Pago anularPago(Long pagoId);

    /** Pago.generarComprobante(): disponible para el administrador y para los residentes del inmueble del pago. */
    PagoResponses.Comprobante generarComprobante(Long pagoId, UsuarioAutenticado usuario);

    /** Residente.consultarHistorialPago(id): pagos del inmueble del residente. */
    Pagina<PagoResponses.Pago> consultarHistorialPago(Long residenteId, SolicitudPagina pagina);

    /** Un pago del inmueble del residente (para consultar si ya quedo aprobado). */
    PagoResponses.Pago consultarPagoDeResidente(Long residenteId, Long pagoId);

    Pagina<PagoResponses.Pago> listar(Long inmuebleId, String estado, SolicitudPagina pagina);
}
