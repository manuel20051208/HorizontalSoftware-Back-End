package unisimon.demo.mapper;

import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioDeInmuebleView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.security.ParTokens;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

import java.util.Arrays;
import java.util.List;

/** Proyecciones de usuarios -> respuestas del API. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsuarioMapper {

    public static UsuarioResponses.Usuario de(CredencialesView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), u.getNotificacionLeida(),
                u.getFechaCreacionCuenta(), inmuebles(u.getInmuebles()), u.getCorreoVerificado(),
                u.getFechaNacimiento(), u.getInicioContrato(), u.getVenceContrato(), u.getVenceAutorizacion());
    }

    public static UsuarioResponses.Usuario de(UsuarioListaView u) {
        return new UsuarioResponses.Usuario(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getCorreoElectronico(), u.getTipoUsuario(), u.getActive(), null, u.getFechaCreacionCuenta(),
                inmuebles(u.getInmuebles()), u.getCorreoVerificado(), u.getFechaNacimiento(),
                u.getInicioContrato(), u.getVenceContrato(), u.getVenceAutorizacion());
    }

    public static AuthResponses.Sesion sesion(ParTokens tokens, UsuarioResponses.Usuario usuario) {
        return new AuthResponses.Sesion(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(),
                tokens.refreshToken(), usuario);
    }

    public static UsuarioResponses.EstadoActivo estadoActivo(Long id, String nombre, boolean active) {
        return new UsuarioResponses.EstadoActivo(id, nombre, active);
    }

    /** La cuenta con todos sus inmuebles (residentes e invitados pueden tener varios). */
    public static UsuarioResponses.UsuarioInmuebles usuarioInmuebles(CredencialesView u,
                                                                     List<InmuebleBasicoView> inmuebles) {
        return new UsuarioResponses.UsuarioInmuebles(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getTipoUsuario(), u.getActive(), inmuebles(inmuebles));
    }

    public static UsuarioResponses.UsuarioDeInmueble usuarioDeInmueble(UsuarioDeInmuebleView u) {
        return new UsuarioResponses.UsuarioDeInmueble(u.getId(), u.getIdentificacion(), u.getNombreCompleto(),
                u.getTipoUsuario(), u.getActive());
    }

    public static List<UsuarioResponses.InmuebleResumen> inmuebles(List<InmuebleBasicoView> inmuebles) {
        if (inmuebles == null) {
            return List.of();
        }
        return inmuebles.stream()
                .map(i -> new UsuarioResponses.InmuebleResumen(i.getId(), i.getDireccionInmueble()))
                .toList();
    }

    /** Convierte "id|direccion;id|direccion" (string_agg de las consultas) en la lista de inmuebles. */
    public static List<UsuarioResponses.InmuebleResumen> inmuebles(String agregado) {
        if (agregado == null || agregado.isBlank()) {
            return List.of();
        }
        return Arrays.stream(agregado.split(";"))
                .filter(fila -> !fila.isBlank())
                .map(fila -> fila.split("\\|", 2))
                .filter(partes -> partes.length == 2)
                .map(partes -> new UsuarioResponses.InmuebleResumen(Long.valueOf(partes[0]), partes[1]))
                .toList();
    }
}
