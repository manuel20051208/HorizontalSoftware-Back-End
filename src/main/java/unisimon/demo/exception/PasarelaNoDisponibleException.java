package unisimon.demo.exception;

import org.springframework.http.HttpStatus;

/** 502: la API de la pasarela no respondio o respondio con error. Wompi reintenta el evento mas tarde. */
public class PasarelaNoDisponibleException extends ApiException {

    public PasarelaNoDisponibleException(String mensaje) {
        super(HttpStatus.BAD_GATEWAY, mensaje);
    }
}
