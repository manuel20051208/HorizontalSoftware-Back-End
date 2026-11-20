package unisimon.demo.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Respuestas sobre usuarios y sus cuentas. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsuarioResponses {

    /** Inmueble resumido (id y direccion) para las respuestas de cuentas. */
    public record InmuebleResumen(Long id, String direccionInmueble) {
    }

    public record Usuario(Long id, Long identificacion, String nombreCompleto, String correoElectronico,
                          String tipoUsuario, Boolean active, Boolean notificacionLeida,
                          LocalDate fechaCreacionCuenta, List<InmuebleResumen> inmuebles,
                          Boolean correoVerificado, LocalDate fechaNacimiento, LocalDate inicioContrato,
                          LocalDate venceContrato, LocalDate venceAutorizacion) {
    }

    /** Resultado de activar/desactivar una cuenta. */
    public record EstadoActivo(Long id, String nombreCompleto, Boolean active) {
    }

    /** Cuenta (residente o invitado) con todos los inmuebles a los que esta relacionada. */
    public record UsuarioInmuebles(Long id, Long identificacion, String nombreCompleto, String tipoUsuario,
                                   Boolean active, List<InmuebleResumen> inmuebles) {
    }

    /** Persona relacionada con un inmueble (residente o invitado). */
    public record UsuarioDeInmueble(Long id, Long identificacion, String nombreCompleto, String tipoUsuario,
                                    Boolean active) {
    }
}
