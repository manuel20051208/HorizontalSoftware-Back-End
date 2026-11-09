package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** 403: el usuario esta autenticado pero no puede hacer esto. */
public class ProhibidoException extends ApiException {

    public ProhibidoException(String mensaje) {
        super(HttpStatus.FORBIDDEN, mensaje);
    }
}
