package unisimon.demo.service;

import unisimon.demo.dto.response.InmuebleResponses;

/** Estado de cuenta de la administracion de un residente. */
public interface EstadoCuentaService {

    /** Residente.consultarEstadoCuenta(inmueble, id): liquidacion vigente, saldo y paz y salvo del inmueble. */
    InmuebleResponses.EstadoCuenta consultarEstadoCuenta(Long residenteId);
}
