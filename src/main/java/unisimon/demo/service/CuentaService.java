package unisimon.demo.service;

import unisimon.demo.dto.response.UsuarioResponses;

/** Operaciones de cuenta que hace el propio usuario sobre su contrasena y su activacion. */
public interface CuentaService {

    /** Usuario.activarCuenta(correo, contrasena): activa una cuenta recien creada con sus credenciales iniciales. */
    UsuarioResponses.Activacion activarCuenta(String correo, String contrasena);

    /** Usuario.recuperarContrasena(): genera una contrasena temporal y la envia por correo. */
    void recuperarContrasena(String correo);

    /** Datos de la cuenta del usuario con sesion iniciada. */
    UsuarioResponses.Usuario consultarCuenta(Long usuarioId);

    /** Usuario.cambiarContrasena(...): cambia la contrasena verificando la actual y cierra las sesiones abiertas. */
    void cambiarContrasena(Long usuarioId, String contrasenaActual, String contrasenaNueva);
}
