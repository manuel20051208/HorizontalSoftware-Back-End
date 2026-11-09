package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** 409: la operacion choca con el estado actual (duplicados, estados invalidos, etc.). */
public class ConflictoException extends ApiException {

    public ConflictoException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
