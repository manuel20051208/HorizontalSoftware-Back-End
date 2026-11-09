package unisimon.demo.service.support;

import org.springframework.stereotype.Component;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.model.Vigilante;
import unisimon.demo.repository.UsuarioRepository;

/** Elige el INSERT nativo que corresponde al tipo de usuario. Evita repetir este switch en los servicios. */
@Component
public class UsuarioPersistidor {

    private final UsuarioRepository usuarios;

    public UsuarioPersistidor(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Devuelve el id generado. */
    public Long insertar(Usuario usuario) {
        return switch (usuario) {
            case Administrador a -> usuarios.insertarAdministrador(a);
            case Vigilante v -> usuarios.insertarVigilante(v);
            case UsuarioAutorizado u -> usuarios.insertarInvitado(u);
            case Residente r -> usuarios.insertarResidente(r);
            default -> throw new IllegalStateException("Tipo de usuario no soportado: " + usuario.getClass());
        };
    }
}
