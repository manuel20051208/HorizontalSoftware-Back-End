package unisimon.demo.policy;

import org.springframework.stereotype.Component;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.security.UsuarioAutenticado;

/** Regla: la informacion de un inmueble solo la ve el administrador o los residentes de ese inmueble. */
@Component
public class AccesoInmueblePolicy {

    private final ResidenteRepository residentes;

    public AccesoInmueblePolicy(ResidenteRepository residentes) {
        this.residentes = residentes;
    }

    public void verificar(UsuarioAutenticado usuario, Long inmuebleId) {
        if (Usuario.ADMINISTRADOR.equals(usuario.rol())) {
            return;
        }
        boolean esDelInmueble = residentes.buscarContexto(usuario.id())
                .map(ContextoResidenteView::getInmuebleId)
                .filter(inmuebleId::equals)
                .isPresent();
        if (!esDelInmueble) {
            throw new ProhibidoException("Este recurso pertenece a otro inmueble");
        }
    }
}
