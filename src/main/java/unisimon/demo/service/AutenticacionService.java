package unisimon.demo.service;

import unisimon.demo.dto.response.AuthResponses;

/** Inicio, renovacion y cierre de sesion (OAuth2: access token + refresh token). */
public interface AutenticacionService {

    /** Usuario.iniciarSesion(correo, contrasena): valida credenciales y abre sesion. */
    AuthResponses.Sesion iniciarSesion(String correo, String contrasena);

    /** Grant "refresh_token": cambia un refresh token vigente por un par nuevo (rotacion). */
    AuthResponses.Sesion renovarSesion(String refreshToken);

    /** Usuario.cerrarSesion(): revoca el refresh token del usuario. */
    void cerrarSesion(Long usuarioId);
}
