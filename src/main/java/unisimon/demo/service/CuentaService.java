package unisimon.demo.service;

import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.UsuarioResponses;

/** Operaciones de cuenta que hace el propio usuario sobre su contrasena y su activacion. */
public interface CuentaService {

    /**
     * Usuario.activarCuenta(correo, contrasena): activa una cuenta recien creada con sus credenciales iniciales y
     * el codigo de 4 cifras que le llego al correo.
     */
    UsuarioResponses.Activacion activarCuenta(String correo, String contrasena, String codigo);

    /** Envia un codigo de activacion nuevo si la cuenta existe y sigue pendiente de activar. No revela si existe. */
    void reenviarCodigo(String correo);

    /** Usuario.recuperarContrasena(): genera una contrasena temporal y la envia por correo. */
    void recuperarContrasena(String correo);

    /** Datos de la cuenta del usuario con sesion iniciada. */
    UsuarioResponses.Usuario consultarCuenta(Long usuarioId);

    /** Usuario.cambiarContrasena(...): cambia la contrasena verificando la actual y cierra las sesiones abiertas. */
    void cambiarContrasena(Long usuarioId, String contrasenaActual, String contrasenaNueva);

    /** Usuario.preferenciasNotificacion del usuario con sesion iniciada. */
    NotificacionResponses.Preferencias consultarPreferencias(Long usuarioId);

    /** Usuario.configurarPreferencias(tipoNotificacion): activa o desactiva ese tipo; exige cuenta activa. */
    NotificacionResponses.Preferencias configurarPreferencias(Long usuarioId, String tipoNotificacion);
}
