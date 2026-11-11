package unisimon.demo.service;

import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.UsuarioResponses;

/** Gestion de cuentas de usuario por parte del administrador. */
public interface UsuarioGestionService {

    /** Administrador.gestionarCuentaUsuario(): crea una cuenta (queda inactiva hasta que el usuario la active). */
    UsuarioResponses.Usuario gestionarCuentaUsuario(UsuarioRequests.CrearUsuario solicitud);

    Pagina<UsuarioResponses.Usuario> listar(String tipoUsuario, Boolean active, SolicitudPagina pagina);

    /** Administrador.modificarActive(identificacion, idToken): activa o desactiva a un usuario no administrador. */
    UsuarioResponses.EstadoActivo modificarActive(Long identificacion);
}
