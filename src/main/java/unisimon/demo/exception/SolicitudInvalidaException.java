package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** 400: los datos enviados no cumplen una regla de negocio. */
public class SolicitudInvalidaException extends ApiException {

    public SolicitudInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
