package unisimon.demo.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * app.pasarela.* : configuracion de la pasarela de pagos (Wompi).
 * <ul>
 *   <li>{@code apiKey}: llave publica, abre el checkout.</li>
 *   <li>{@code integritySecret}: SECRETO, firma el checkout (signature:integrity).</li>
 *   <li>{@code eventsSecret}: SECRETO, verifica la firma de los eventos del webhook (signature.checksum).</li>
 *   <li>{@code privateKey}: SECRETO, autentica las consultas del back a la API REST de Wompi.</li>
 *   <li>{@code urlApiRest}: base de la API REST (sandbox o produccion).</li>
 *   <li>{@code redirectUrl}: pantalla del front a la que vuelve el residente (opcional).</li>
 * </ul>
 * Los secretos solo viven en el entorno: no se guardan en base de datos ni salen en respuestas o logs.
 */
@ConfigurationProperties(prefix = "app.pasarela")
public record PasarelaProperties(String nombre, String urlApi, String apiKey, String integritySecret,
                                 String eventsSecret, String privateKey, String urlApiRest, String redirectUrl) {

    /** Nombres de las variables de entorno obligatorias que estan vacias (para avisar al arrancar). */
    public List<String> secretosFaltantes() {
        List<String> faltan = new ArrayList<>();
        if (vacio(integritySecret)) {
            faltan.add("PASARELA_INTEGRITY_SECRET");
        }
        if (vacio(eventsSecret)) {
            faltan.add("PASARELA_EVENTS_SECRET");
        }
        if (vacio(privateKey)) {
            faltan.add("PASARELA_PRIVATE_KEY");
        }
        if (vacio(urlApiRest)) {
            faltan.add("PASARELA_URL_API_REST");
        }
        return faltan;
    }

    public static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    /** Nunca imprime los secretos (los records los incluirian por defecto en logs y trazas). */
    @Override
    public String toString() {
        return "PasarelaProperties[nombre=" + nombre + ", urlApi=" + urlApi + ", apiKey=" + apiKey
                + ", integritySecret=" + oculto(integritySecret) + ", eventsSecret=" + oculto(eventsSecret)
                + ", privateKey=" + oculto(privateKey) + ", urlApiRest=" + urlApiRest
                + ", redirectUrl=" + redirectUrl + "]";
    }

    private static String oculto(String secreto) {
        return vacio(secreto) ? "<vacio>" : "****";
    }
}
