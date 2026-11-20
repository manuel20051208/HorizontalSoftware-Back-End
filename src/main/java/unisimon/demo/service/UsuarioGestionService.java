package unisimon.demo.service;

import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.UsuarioResponses;

/** Gestion de cuentas de usuario por parte del administrador. */
public interface UsuarioGestionService {

    /**
     * Administrador.gestionarCuentaUsuario(): crea una cuenta inactiva y envia un codigo de 4 cifras al correo.
     * La cuenta se activa solo cuando el administrador confirma ese codigo con {@link #verificarCorreo}.
     */
    UsuarioResponses.Usuario gestionarCuentaUsuario(UsuarioRequests.CrearUsuario solicitud);

    /**
     * El administrador confirma el codigo que llego al correo del usuario: el correo queda verificado y la cuenta
     * activa (Usuario.activarCuenta).
     */
    UsuarioResponses.Usuario verificarCorreo(Long identificacion, String codigo);

    /** Envia un codigo nuevo al correo de una cuenta pendiente de verificar; reemplaza al anterior. Devuelve el correo. */
    String reenviarCodigo(Long identificacion);

    Pagina<UsuarioResponses.Usuario> listar(String tipoUsuario, Boolean active, SolicitudPagina pagina);

    /**
     * Administrador.modificarActive(identificacion, idToken): activa o desactiva a un usuario no administrador.
     * No activa una cuenta cuyo correo no se ha verificado.
     */
    UsuarioResponses.EstadoActivo modificarActive(Long identificacion);

    UsuarioResponses.Usuario consultar(Long identificacion);

    /** Datos personales y de contrato; un correo nuevo deja la cuenta inactiva hasta verificarlo. */
    UsuarioResponses.Usuario actualizar(Long identificacion, UsuarioRequests.ActualizarUsuario solicitud);

    /** Borra la cuenta respetando composicion y agregacion; el administrador no puede borrarse a si mismo. */
    void eliminar(Long adminId, Long identificacion);
}
