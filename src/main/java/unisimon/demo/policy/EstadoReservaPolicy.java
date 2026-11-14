package unisimon.demo.policy;

import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.model.ReservaZonaComun;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

import java.util.Locale;
import java.util.Set;

/** Valida el filtro {@code ?estado=} de los listados de reservas. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EstadoReservaPolicy {

    public static final Set<String> ESTADOS =
            Set.of(ReservaZonaComun.PROGRAMADA, ReservaZonaComun.EN_CURSO, ReservaZonaComun.FINALIZADA);

    /** null o vacio = sin filtro; si no, el estado en mayusculas. Un valor desconocido responde 400. */
    public static String normalizarFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalizado)) {
            throw new SolicitudInvalidaException("estado debe ser PROGRAMADA, EN_CURSO o FINALIZADA");
        }
        return normalizado;
    }
}
