package unisimon.demo.cache;

/**
 * Nombres (ID) de los caches de la aplicacion. Toda anotacion {@code @Cacheable}/{@code @CacheEvict} usa estas
 * constantes, nunca el texto suelto: asi un cache se renombra en un solo sitio y no hay nombres mal escritos.
 */
public final class NombresCache {

    /** Listado de inmuebles con su estado de pago y valor de parqueadero (plano de torres). */
    public static final String INMUEBLES = "inmuebles";

    /** Listado completo de zonas comunes (administracion), con su contador de reservas. */
    public static final String ZONAS_COMUNES = "zonasComunes";

    /** Zonas activas asignadas a un inmueble; la llave es el id del inmueble. */
    public static final String ZONAS_POR_INMUEBLE = "zonasPorInmueble";

    /** Listado de reglas de reserva con las zonas donde aplican. */
    public static final String REGLAS_RESERVA = "reglasReserva";

    /** Configuracion de la pasarela de pagos tal como la devuelve el API (llave enmascarada). */
    public static final String PASARELA_PAGO = "pasarelaPago";

    /** Todos los caches que existen: el CacheManager solo crea estos, un nombre desconocido falla al usarse. */
    public static final String[] TODOS = {INMUEBLES, ZONAS_COMUNES, ZONAS_POR_INMUEBLE, REGLAS_RESERVA,
            PASARELA_PAGO};

    private NombresCache() {
    }
}
