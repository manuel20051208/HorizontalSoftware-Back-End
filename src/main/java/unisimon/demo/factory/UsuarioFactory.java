package unisimon.demo.factory;

import org.springframework.stereotype.Component;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.Vigilante;

import java.util.Locale;

/**
 * Crea el tipo correcto de usuario (Administrador, Residente o Vigilante) a partir de una solicitud, usando
 * los constructores del diagrama. Es el unico lugar que conoce que datos pide cada tipo.
 */
@Component
public class UsuarioFactory {

    public Usuario crear(UsuarioRequests.CrearUsuario s, String contrasenaCodificada) {
        String tipo = s.tipoUsuario() == null ? "" : s.tipoUsuario().trim().toUpperCase(Locale.ROOT);
        String nombre = s.nombreCompleto().trim();
        String correo = s.correoElectronico().trim().toLowerCase(Locale.ROOT);

        return switch (tipo) {
            case Usuario.ADMINISTRADOR -> {
                validarContrato(s);
                yield new Administrador(null, s.identificacion(), nombre, s.fechaNacimiento(), correo,
                        contrasenaCodificada, tipo, s.inicioContrato(), s.venceContrato());
            }
            case Usuario.VIGILANTE -> {
                validarContrato(s);
                yield new Vigilante(null, s.identificacion(), nombre, s.fechaNacimiento(), correo,
                        contrasenaCodificada, tipo, s.inicioContrato(), s.venceContrato());
            }
            case Usuario.RESIDENTE -> {
                if (s.inmuebleId() == null) {
                    throw new SolicitudInvalidaException("inmuebleId es obligatorio para un RESIDENTE");
                }
                yield new Residente(null, s.identificacion(), nombre, s.fechaNacimiento(), correo,
                        contrasenaCodificada, tipo, Hidratador.referenciaInmueble(s.inmuebleId()), null);
            }
            default -> throw new SolicitudInvalidaException(
                    "tipoUsuario debe ser ADMINISTRADOR, RESIDENTE o VIGILANTE");
        };
    }

    private static void validarContrato(UsuarioRequests.CrearUsuario s) {
        if (s.inicioContrato() == null || s.venceContrato() == null) {
            throw new SolicitudInvalidaException("inicioContrato y venceContrato son obligatorios para este tipo");
        }
        if (s.venceContrato().isBefore(s.inicioContrato())) {
            throw new SolicitudInvalidaException("venceContrato no puede ser anterior a inicioContrato");
        }
    }
}
