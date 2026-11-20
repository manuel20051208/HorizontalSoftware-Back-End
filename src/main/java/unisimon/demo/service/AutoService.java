package unisimon.demo.service;

import unisimon.demo.dto.request.AutoRequests;
import unisimon.demo.dto.response.AutoResponses;

/** Autos de los residentes. */
public interface AutoService {

    /** Registra el auto usando el parqueadero del inmueble elegido por el residente. */
    AutoResponses.Auto registrarAuto(Long residenteId, Long inmuebleId, AutoRequests.RegistrarAuto solicitud);

    AutoResponses.Auto consultarAuto(Long residenteId);

    /** Auto.consultarDuenos(inmueble, id) */
    AutoResponses.Duenos consultarDuenos(Long inmuebleId, Long autoId);

    /** Cambia la placa y el parqueadero (nulo: sin parqueadero) del auto del residente. */
    AutoResponses.Auto actualizarAuto(Long residenteId, AutoRequests.ActualizarAuto solicitud);

    void eliminarAuto(Long residenteId);
}
