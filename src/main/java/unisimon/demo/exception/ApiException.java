package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** Error de negocio que el API traduce a una respuesta HTTP. Las subclases fijan el estado. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
