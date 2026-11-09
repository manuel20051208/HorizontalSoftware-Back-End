package unisimon.demo.dto.response;

import java.time.LocalDate;

/** Respuestas sobre usuarios y sus cuentas. */
public final class UsuarioResponses {

    private UsuarioResponses() {
    }

    public record Usuario(Long id, Long identificacion, String nombreCompleto, String correoElectronico,
                          String tipoUsuario, Boolean active, Boolean notificacionLeida,
                          LocalDate fechaCreacionCuenta, Long inmuebleId, String direccionInmueble) {
    }

    /** Resultado de activar/desactivar una cuenta. */
    public record EstadoActivo(Long id, String nombreCompleto, Boolean active) {
    }

    public record Activacion(Boolean active, String mensaje) {
    }
}
