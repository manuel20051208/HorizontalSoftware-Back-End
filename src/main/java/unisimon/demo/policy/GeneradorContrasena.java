package unisimon.demo.policy;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Genera contrasenas aleatorias (temporales de recuperacion y claves internas de invitados). */
@Component
public class GeneradorContrasena {

    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final int LONGITUD = 12;

    private final SecureRandom azar = new SecureRandom();

    /** Siempre cumple {@link PoliticaContrasena}: mezcla letras y numeros. */
    public String generar() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        sb.append(LETRAS.charAt(azar.nextInt(LETRAS.length())));
        sb.append(NUMEROS.charAt(azar.nextInt(NUMEROS.length())));
        String todo = LETRAS + NUMEROS;
        while (sb.length() < LONGITUD) {
            sb.append(todo.charAt(azar.nextInt(todo.length())));
        }
        return sb.toString();
    }
}
