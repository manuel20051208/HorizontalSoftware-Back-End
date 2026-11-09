package unisimon.demo.support;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Crea proyecciones (las interfaces de solo lectura de los repositorios) para las pruebas, sin base de datos.
 * Uso: {@code Proyecciones.de(CredencialesView.class, "id", 1L, "active", true)}; los getters que no se
 * indiquen devuelven null.
 */
public final class Proyecciones {

    private Proyecciones() {
    }

    public static <T> T de(Class<T> tipo, Object... pares) {
        if (pares.length % 2 != 0) {
            throw new IllegalArgumentException("Los valores deben venir en pares nombre, valor");
        }
        Map<String, Object> valores = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            valores.put((String) pares[i], pares[i + 1]);
        }
        Object proxy = Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (p, metodo, args) -> {
            String nombre = metodo.getName();
            if (nombre.startsWith("get") && nombre.length() > 3) {
                String clave = Character.toLowerCase(nombre.charAt(3)) + nombre.substring(4);
                return valores.get(clave);
            }
            return switch (nombre) {
                case "toString" -> tipo.getSimpleName() + valores;
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == args[0];
                default -> throw new UnsupportedOperationException(nombre);
            };
        });
        return tipo.cast(proxy);
    }
}
