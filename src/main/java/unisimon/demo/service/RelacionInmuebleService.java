package unisimon.demo.service;

import unisimon.demo.dto.response.UsuarioResponses;

import java.util.List;

/**
 * Relacion N:M de las cuentas de residentes e invitados con los inmuebles: consultar, vincular, mudar, quitar y
 * listar las personas de un inmueble. Todas las operaciones las autoriza el administrador que las pide.
 */
public interface RelacionInmuebleService {

    /** Los inmuebles de la cuenta (residentes e invitados pueden tener varios). */
    UsuarioResponses.UsuarioInmuebles consultar(Long adminId, Long identificacion);

    /** Agrega un inmueble a la cuenta; no activa ni verifica el correo. */
    UsuarioResponses.UsuarioInmuebles vincular(Long adminId, Long identificacion, Long inmuebleId);

    /**
     * Mudanza: quita el inmueble de origen y agrega el destino. Arrastra invitados, libera el parqueadero del
     * origen y mueve (o cancela) las reservas futuras.
     */
    UsuarioResponses.UsuarioInmuebles mudar(Long adminId, Long identificacion, Long inmuebleOrigenId,
                                            Long inmuebleDestinoId);

    /**
     * Quita un inmueble a la cuenta. Si se queda sin ninguno y estaba activa, la desactiva y cierra su sesion.
     */
    void desvincular(Long adminId, Long identificacion, Long inmuebleId);

    /** Residentes e invitados relacionados con el inmueble, por tipo y nombre. */
    List<UsuarioResponses.UsuarioDeInmueble> listarUsuariosDeInmueble(Long adminId, Long inmuebleId);
}
