package unisimon.demo.mapper;

import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.security.ParTokens;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

/** Proyecciones de usuarios -> respuestas del API. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsuarioMapper {

    public static UsuarioResponses.Usuario de(CredencialesView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), u.getNotificacionLeida(),
                u.getFechaCreacionCuenta(), u.getInmuebleId(), null, u.getCorreoVerificado());
    }

    public static UsuarioResponses.Usuario de(UsuarioListaView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), null, u.getFechaCreacionCuenta(),
                u.getInmuebleId(), u.getDireccionInmueble(), u.getCorreoVerificado());
    }

    public static AuthResponses.Sesion sesion(ParTokens tokens, UsuarioResponses.Usuario usuario) {
        return new AuthResponses.Sesion(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(),
                tokens.refreshToken(), usuario);
    }

    public static UsuarioResponses.EstadoActivo estadoActivo(Long id, String nombre, boolean active) {
        return new UsuarioResponses.EstadoActivo(id, nombre, active);
    }

    /** La cuenta con el inmueble indicado (el servicio pasa el nuevo al vincular o cambiar). */
    public static UsuarioResponses.RelacionInmueble relacionInmueble(CredencialesView u, Long inmuebleId,
                                                                     String direccionInmueble) {
        return new UsuarioResponses.RelacionInmueble(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getTipoUsuario(), u.getActive(), inmuebleId, direccionInmueble);
    }

    /** Fila del listado de residentes de un inmueble (ya trae la direccion). */
    public static UsuarioResponses.RelacionInmueble relacionInmueble(UsuarioListaView u) {
        return new UsuarioResponses.RelacionInmueble(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getTipoUsuario(), u.getActive(), u.getInmuebleId(), u.getDireccionInmueble());
    }
}
