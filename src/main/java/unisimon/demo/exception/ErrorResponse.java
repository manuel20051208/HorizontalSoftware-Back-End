package unisimon.demo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Cuerpo estandar de todos los errores del API. {@code errores} solo viene en validaciones (campo -> mensaje). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String error, String mensaje, Map<String, String> errores) {

    public static ErrorResponse de(int status, String error, String mensaje) {
        return new ErrorResponse(Instant.now(), status, error, mensaje, null);
    }

    public static ErrorResponse de(int status, String error, String mensaje, Map<String, String> errores) {
        return new ErrorResponse(Instant.now(), status, error, mensaje, errores);
    }
}
