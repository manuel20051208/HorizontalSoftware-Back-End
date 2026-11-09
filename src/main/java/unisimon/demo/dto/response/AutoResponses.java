package unisimon.demo.dto.response;

import java.util.List;

/** Respuestas sobre autos. */
public final class AutoResponses {

    private AutoResponses() {
    }

    public record Auto(Long id, Long duenoId, String nombreDueno, Long placa, Long parqueaderoId, Long inmuebleId,
                       String direccionInmueble) {
    }

    public record Dueno(Long id, Long identificacion, String nombreCompleto, Long inmuebleId,
                        String direccionInmueble) {
    }

    public record Duenos(Long autoId, Long inmuebleId, List<Dueno> duenos) {
    }
}
