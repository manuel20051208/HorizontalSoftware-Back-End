package unisimon.demo.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.UsuarioRepository;

import java.util.List;

/**
 * Resuelve sobre que inmueble opera el residente cuando tiene varios. El cliente lo elige con {@code ?inmuebleId=};
 * si no viene y solo tiene uno se usa ese. Un inmueble ajeno responde 403, varios sin elegir 400 y ninguno 409.
 */
@Component
@RequiredArgsConstructor
public class InmuebleDelResidente {

    private final UsuarioRepository usuarios;

    public InmuebleBasicoView resolver(Long residenteId, Long inmuebleId) {
        List<InmuebleBasicoView> propios = usuarios.listarInmueblesDe(residenteId);
        if (propios.isEmpty()) {
            throw new ConflictoException("Primero relacione la cuenta con un inmueble");
        }
        if (inmuebleId != null) {
            return propios.stream()
                    .filter(i -> inmuebleId.equals(i.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ProhibidoException("Ese inmueble no es suyo"));
        }
        if (propios.size() > 1) {
            throw new SolicitudInvalidaException("Indique el inmueble (inmuebleId)");
        }
        return propios.get(0);
    }

    public List<InmuebleBasicoView> listar(Long residenteId) {
        return usuarios.listarInmueblesDe(residenteId);
    }
}
