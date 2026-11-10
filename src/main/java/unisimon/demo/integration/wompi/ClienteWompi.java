package unisimon.demo.integration.wompi;

/** Puerto hacia la API REST de Wompi (permite cambiar la implementacion o simularla en pruebas). */
public interface ClienteWompi {

    /**
     * Consulta la transaccion en Wompi con la llave privada.
     *
     * @throws unisimon.demo.exception.PasarelaNoDisponibleException si Wompi no responde o responde con error.
     */
    TransaccionWompi consultarTransaccion(String idTransaccion);
}
