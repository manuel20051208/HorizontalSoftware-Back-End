package unisimon.demo.service;

import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.UsuarioResponses;

import java.util.List;

/** Estado de cuenta de la administracion de un residente. */
public interface EstadoCuentaService {

    /**
     * Residente.consultarEstadoCuenta(inmueble, id): liquidacion vigente, saldo y paz y salvo del inmueble
     * elegido con {@code inmuebleId} (o del unico que tenga).
     */
    InmuebleResponses.EstadoCuenta consultarEstadoCuenta(Long residenteId, Long inmuebleId);

    /** Los inmuebles del residente, para el selector de apartamento. */
    List<UsuarioResponses.InmuebleResumen> inmuebles(Long residenteId);
}
