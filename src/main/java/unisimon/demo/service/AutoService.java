package unisimon.demo.service;

import unisimon.demo.dto.request.AutoRequests;
import unisimon.demo.dto.response.AutoResponses;

/** Autos de los residentes. */
public interface AutoService {

    AutoResponses.Auto registrarAuto(Long residenteId, AutoRequests.RegistrarAuto solicitud);

    AutoResponses.Auto consultarAuto(Long residenteId);

    /** Auto.consultarDuenos(inmueble, id) */
    AutoResponses.Duenos consultarDuenos(Long inmuebleId, Long autoId);
}
