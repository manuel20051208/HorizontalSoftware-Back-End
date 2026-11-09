package unisimon.demo.policy;

import org.springframework.stereotype.Component;
import unisimon.demo.exception.SolicitudInvalidaException;

/** Reglas que debe cumplir una contrasena nueva: minimo 8 caracteres, con al menos una letra y un numero. */
@Component
public class PoliticaContrasena {

    public static final int LONGITUD_MINIMA = 8;

    public void validar(String contrasena) {
        if (contrasena == null || contrasena.length() < LONGITUD_MINIMA) {
            throw new SolicitudInvalidaException("La contrasena debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        boolean tieneLetra = contrasena.chars().anyMatch(Character::isLetter);
        boolean tieneNumero = contrasena.chars().anyMatch(Character::isDigit);
        if (!tieneLetra || !tieneNumero) {
            throw new SolicitudInvalidaException("La contrasena debe tener al menos una letra y un numero");
        }
    }
}
