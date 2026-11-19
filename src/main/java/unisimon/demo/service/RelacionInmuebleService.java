package unisimon.demo.service;

import unisimon.demo.dto.response.UsuarioResponses;

import java.util.List;

/** Relacion de las cuentas de residente con un inmueble: consultar, listar, vincular, cambiar y desvincular. */
public interface RelacionInmuebleService {

    /** El inmueble de la cuenta (null si no tiene). Solo cuentas de residente. */
    UsuarioResponses.RelacionInmueble consultar(Long adminId, Long identificacion);

    /** Los residentes de un inmueble, por nombre. */
    List<UsuarioResponses.RelacionInmueble> listarPorInmueble(Long adminId, Long inmuebleId);

    /** Vincula una cuenta de residente sin inmueble; no reactiva la cuenta. */
    UsuarioResponses.RelacionInmueble vincular(Long adminId, Long identificacion, Long inmuebleId);

    /**
     * Cambia el inmueble de un residente: los invitados exclusivos se mudan con el, el auto libera el parqueadero
     * viejo y se cancelan las reservas futuras de zonas que no tenga el inmueble nuevo.
     */
    UsuarioResponses.RelacionInmueble cambiar(Long adminId, Long identificacion, Long inmuebleId);

    /**
     * Desvincula la cuenta: sus invitados exclusivos quedan inactivos, el auto libera el parqueadero, se cancelan
     * sus reservas futuras y la cuenta, si estaba activa, queda inactiva y sin sesion.
     */
    void desvincular(Long adminId, Long identificacion);
}
