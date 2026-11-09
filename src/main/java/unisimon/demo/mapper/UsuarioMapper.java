package unisimon.demo.mapper;

import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.security.ParTokens;

/** Proyecciones de usuarios -> respuestas del API. */
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponses.Usuario de(CredencialesView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), u.getNotificacionLeida(),
                u.getFechaCreacionCuenta(), u.getInmuebleId(), null);
    }

    public static UsuarioResponses.Usuario de(UsuarioListaView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), null, u.getFechaCreacionCuenta(),
                u.getInmuebleId(), u.getDireccionInmueble());
    }

    public static AuthResponses.Sesion sesion(ParTokens tokens, UsuarioResponses.Usuario usuario) {
        return new AuthResponses.Sesion(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(),
                tokens.refreshToken(), usuario);
    }

    public static UsuarioResponses.EstadoActivo estadoActivo(Long id, String nombre, boolean active) {
        return new UsuarioResponses.EstadoActivo(id, nombre, active);
    }
}
