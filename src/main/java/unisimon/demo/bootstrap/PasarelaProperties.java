package unisimon.demo.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * app.pasarela.* : configuracion de la pasarela de pagos (Wompi). El .env es la UNICA fuente de verdad:
 * la tabla pasarelas_pago se sincroniza con estos valores al arrancar.
 * <ul>
 *   <li>{@code nombre}: nombre de la pasarela.</li>
 *   <li>{@code urlApi}: URL del checkout web (https://checkout.wompi.co/p/).</li>
 *   <li>{@code apiKey}: llave publica (pub_prod_... / pub_test_...), abre el checkout.</li>
 *   <li>{@code integritySecret}: SECRETO (prod_integrity_... / test_integrity_...), firma el checkout.</li>
 *   <li>{@code eventsSecret}: SECRETO (prod_events_... / test_events_...), verifica la firma del webhook.</li>
 *   <li>{@code privateKey}: SECRETO (prv_prod_... / prv_test_...), autentica las consultas a la API REST.</li>
 *   <li>{@code urlApiRest}: https://production.wompi.co/v1 o https://sandbox.wompi.co/v1.</li>
 *   <li>{@code redirectUrl}: pantalla del front a la que vuelve el residente (opcional).</li>
 * </ul>
 * Los secretos no se guardan en base de datos ni salen en respuestas o logs.
 */
@ConfigurationProperties(prefix = "app.pasarela")
public record PasarelaProperties(String nombre, String urlApi, String apiKey, String integritySecret,
                                 String eventsSecret, String privateKey, String urlApiRest, String redirectUrl) {

    public static final String PRODUCCION = "produccion";
    public static final String PRUEBAS = "pruebas";

    static final String HOST_CHECKOUT = "checkout.wompi.co";
    static final String HOST_API_PRODUCCION = "production.wompi.co";
    static final String HOST_API_PRUEBAS = "sandbox.wompi.co";

    /**
     * Lista de problemas de configuracion (vacia = lista para cobrar). Cada mensaje nombra la variable de entorno,
     * nunca su valor, asi que es seguro escribirlos en el log.
     */
    public List<String> problemas() {
        List<String> problemas = new ArrayList<>();
        Map<String, String> ambientes = new LinkedHashMap<>();

        if (vacio(nombre)) {
            problemas.add("PASARELA_NOMBRE esta vacia");
        }
        validarUrlCheckout(problemas);
        llave("PASARELA_API_KEY", apiKey, "pub_prod_", "pub_test_", problemas, ambientes);
        llave("PASARELA_INTEGRITY_SECRET", integritySecret, "prod_integrity_", "test_integrity_", problemas, ambientes);
        llave("PASARELA_EVENTS_SECRET", eventsSecret, "prod_events_", "test_events_", problemas, ambientes);
        llave("PASARELA_PRIVATE_KEY", privateKey, "prv_prod_", "prv_test_", problemas, ambientes);
        validarUrlApiRest(problemas, ambientes);
        validarRedirect(problemas);

        if (ambientes.values().stream().distinct().count() > 1) {
            StringBuilder detalle = new StringBuilder();
            ambientes.forEach((variable, ambiente) ->
                    detalle.append(detalle.isEmpty() ? "" : ", ").append(variable).append('=').append(ambiente));
            problemas.add("Ambientes mezclados (todas deben ser de produccion o todas de pruebas): " + detalle);
        }
        return problemas;
    }

    public boolean listaParaCobrar() {
        return problemas().isEmpty();
    }

    /** produccion o pruebas segun la llave publica; null si no se reconoce. */
    public String ambiente() {
        if (apiKey == null) {
            return null;
        }
        String llave = apiKey.trim();
        return llave.startsWith("pub_prod_") ? PRODUCCION : llave.startsWith("pub_test_") ? PRUEBAS : null;
    }

    public static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static void llave(String variable, String valor, String prefijoProd, String prefijoPruebas,
                              List<String> problemas, Map<String, String> ambientes) {
        if (vacio(valor)) {
            problemas.add(variable + " esta vacia");
            return;
        }
        String v = valor.trim();
        if (!v.equals(valor) || v.chars().anyMatch(Character::isWhitespace)) {
            problemas.add(variable + " tiene espacios o saltos de linea");
        }
        if (v.startsWith(prefijoProd) && v.length() > prefijoProd.length()) {
            ambientes.put(variable, PRODUCCION);
        } else if (v.startsWith(prefijoPruebas) && v.length() > prefijoPruebas.length()) {
            ambientes.put(variable, PRUEBAS);
        } else {
            problemas.add(variable + " debe empezar por " + prefijoProd + " o " + prefijoPruebas);
        }
        if (v.toUpperCase(Locale.ROOT).contains("CAMBIAR")) {
            problemas.add(variable + " sigue con el valor de ejemplo");
        }
    }

    private void validarUrlCheckout(List<String> problemas) {
        URI uri = uriHttps("PASARELA_URL_API", urlApi, problemas);
        if (uri != null && !HOST_CHECKOUT.equalsIgnoreCase(uri.getHost())) {
            problemas.add("PASARELA_URL_API debe ser https://" + HOST_CHECKOUT + "/p/");
        }
    }

    private void validarUrlApiRest(List<String> problemas, Map<String, String> ambientes) {
        URI uri = uriHttps("PASARELA_URL_API_REST", urlApiRest, problemas);
        if (uri == null) {
            return;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String ruta = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        if (!"/v1".equals(ruta) || !(HOST_API_PRODUCCION.equals(host) || HOST_API_PRUEBAS.equals(host))) {
            problemas.add("PASARELA_URL_API_REST debe ser https://" + HOST_API_PRODUCCION + "/v1 o https://"
                    + HOST_API_PRUEBAS + "/v1");
            return;
        }
        ambientes.put("PASARELA_URL_API_REST", HOST_API_PRODUCCION.equals(host) ? PRODUCCION : PRUEBAS);
    }

    private void validarRedirect(List<String> problemas) {
        if (vacio(redirectUrl)) {
            return;   // opcional: sin ella el residente se queda en la pantalla de Wompi
        }
        URI uri = uri(redirectUrl);
        boolean local = uri != null && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (uri == null || uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || (local && "http".equalsIgnoreCase(uri.getScheme())))) {
            problemas.add("PASARELA_REDIRECT_URL debe ser una URL https completa (http solo para localhost)");
        }
    }

    private static URI uriHttps(String variable, String valor, List<String> problemas) {
        if (vacio(valor)) {
            problemas.add(variable + " esta vacia");
            return null;
        }
        URI uri = uri(valor.trim());
        if (uri == null || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            problemas.add(variable + " debe ser una URL https valida");
            return null;
        }
        return uri;
    }

    private static URI uri(String valor) {
        try {
            return new URI(valor.trim());
        } catch (Exception e) {
            return null;
        }
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
