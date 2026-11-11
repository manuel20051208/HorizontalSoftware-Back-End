package unisimon.demo.service;

import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.Pagina;

/** Alta y consulta de inmuebles (apartamentos). */
public interface InmuebleService {

    InmuebleResponses.Detalle crear(InmuebleRequests.CrearInmueble solicitud);

    /** Todos los inmuebles con su estado de pago: es lo que pinta el plano de torres. */
    Pagina<InmuebleResponses.Resumen> listar(SolicitudPagina pagina);

    /** Detalle con usuarios (Inmueble.mostrarUsuarios), zonas asignadas y liquidacion vigente. */
    InmuebleResponses.Detalle detalle(Long inmuebleId);
}
