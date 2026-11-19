package unisimon.demo.policy;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Genera los codigos numericos de verificacion que se envian por correo (activacion de cuenta). */
@Component
public class GeneradorCodigo {

    public static final int DIGITOS = 4;

    private final SecureRandom azar = new SecureRandom();

    /** Codigo de {@value #DIGITOS} cifras, con ceros a la izquierda (de "0000" a "9999"). */
    public String generar() {
        return String.format("%0" + DIGITOS + "d", azar.nextInt((int) Math.pow(10, DIGITOS)));
    }
}
