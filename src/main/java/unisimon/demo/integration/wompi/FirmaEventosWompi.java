package unisimon.demo.integration.wompi;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verificacion de la firma de los eventos (webhook) de Wompi.
 * <p>
 * checksum = SHA-256( valores de signature.properties (en orden, leidos dentro de "data") + timestamp + secreto de eventos ).
 * Sin el secreto de eventos nadie puede fabricar un checksum valido.
 */
public final class FirmaEventosWompi {

    private FirmaEventosWompi() {
    }

    public static boolean esValida(Map<String, Object> evento, String secretoEventos) {
        if (evento == null || secretoEventos == null || secretoEventos.isBlank()) {
            return false;
        }
        if (!(evento.get("signature") instanceof Map<?, ?> firma)
                || !(firma.get("properties") instanceof List<?> propiedades) || propiedades.isEmpty()
                || !(firma.get("checksum") instanceof String checksum)
                || !(evento.get("data") instanceof Map<?, ?> data)) {
            return false;
        }
        Object timestamp = evento.get("timestamp");
        if (timestamp == null) {
            return false;
        }
        StringBuilder cadena = new StringBuilder();
        for (Object propiedad : propiedades) {
            Object valor = propiedad == null ? null : valor(data, propiedad.toString());
            if (valor == null) {
                return false;
            }
            cadena.append(comoTexto(valor));
        }
        cadena.append(comoTexto(timestamp)).append(secretoEventos);

        byte[] esperado = sha256Hex(cadena.toString()).getBytes(StandardCharsets.UTF_8);
        byte[] recibido = checksum.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recibido);   // tiempo constante
    }

    /** Lee una ruta con puntos ("transaction.id") dentro de un mapa anidado. */
    public static Object valor(Map<?, ?> raiz, String ruta) {
        Object actual = raiz;
        for (String parte : ruta.split("\\.")) {
            if (!(actual instanceof Map<?, ?> mapa)) {
                return null;
            }
            actual = mapa.get(parte);
        }
        return actual;
    }

    private static String comoTexto(Object valor) {
        if (valor instanceof Double || valor instanceof Float || valor instanceof BigDecimal) {
            return new BigDecimal(valor.toString()).stripTrailingZeros().toPlainString();
        }
        return valor.toString();
    }

    static String sha256Hex(String texto) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
