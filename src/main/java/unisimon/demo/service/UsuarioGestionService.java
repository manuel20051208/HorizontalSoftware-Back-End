package unisimon.demo.service;

import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.UsuarioResponses;

import java.util.List;

/** Gestion de cuentas de usuario por parte del administrador. */
public interface UsuarioGestionService {

    /** Administrador.gestionarCuentaUsuario(): crea una cuenta (queda inactiva hasta que el usuario la active). */
    UsuarioResponses.Usuario gestionarCuentaUsuario(UsuarioRequests.CrearUsuario solicitud);

    List<UsuarioResponses.Usuario> listar(String tipoUsuario, Boolean active);

    /** Administrador.modificarActive(identificacion, idToken): activa o desactiva a un usuario no administrador. */
    UsuarioResponses.EstadoActivo modificarActive(Long identificacion);
}
