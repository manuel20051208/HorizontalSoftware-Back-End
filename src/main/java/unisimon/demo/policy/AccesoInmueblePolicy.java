package unisimon.demo.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.security.UsuarioAutenticado;

/** Regla: la informacion de un inmueble solo la ve el administrador o las personas relacionadas con el. */
@Component
@RequiredArgsConstructor
public class AccesoInmueblePolicy {

    private final UsuarioRepository usuarios;

    public void verificar(UsuarioAutenticado usuario, Long inmuebleId) {
        if (Usuario.ADMINISTRADOR.equals(usuario.rol())) {
            return;
        }
        if (inmuebleId == null || !usuarios.estaRelacionado(usuario.id(), inmuebleId)) {
            throw new ProhibidoException("Este recurso pertenece a otro inmueble");
        }
    }
}
