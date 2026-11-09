package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** 401: credenciales o token invalidos. */
public class NoAutenticadoException extends ApiException {

    public NoAutenticadoException(String mensaje) {
        super(HttpStatus.UNAUTHORIZED, mensaje);
    }
}
