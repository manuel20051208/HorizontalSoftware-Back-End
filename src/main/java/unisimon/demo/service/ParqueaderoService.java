package unisimon.demo.service;

import unisimon.demo.dto.request.ParqueaderoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ParqueaderoResponses;

/** CRUD de parqueaderos (Paqueadero), parte por composicion de un inmueble. */
public interface ParqueaderoService {

    ParqueaderoResponses.Parqueadero crear(ParqueaderoRequests.CrearParqueadero solicitud);

    Pagina<ParqueaderoResponses.Parqueadero> listar(SolicitudPagina pagina);

    ParqueaderoResponses.Parqueadero consultar(Long parqueaderoId);

    ParqueaderoResponses.Parqueadero actualizar(Long parqueaderoId, ParqueaderoRequests.ActualizarParqueadero solicitud);

    /** Auto -> Paqueadero es asociacion: el auto que lo usaba queda sin parqueadero, no se borra. */
    void eliminar(Long parqueaderoId);
}
