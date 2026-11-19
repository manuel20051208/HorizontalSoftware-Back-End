package unisimon.demo.dto.response;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas sobre usuarios y sus cuentas. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsuarioResponses {

    public record Usuario(Long id, Long identificacion, String nombreCompleto, String correoElectronico,
                          String tipoUsuario, Boolean active, Boolean notificacionLeida,
                          LocalDate fechaCreacionCuenta, Long inmuebleId, String direccionInmueble,
                          Boolean correoVerificado) {
    }

    /** Resultado de activar/desactivar una cuenta. */
    public record EstadoActivo(Long id, String nombreCompleto, Boolean active) {
    }

    /** Cuenta de residente con el inmueble al que esta relacionada (null si no tiene). */
    public record RelacionInmueble(Long id, Long identificacion, String nombreCompleto, String tipoUsuario,
                                   Boolean active, Long inmuebleId, String direccionInmueble) {
    }
}
