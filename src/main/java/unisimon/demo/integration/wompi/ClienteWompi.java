package unisimon.demo.integration.wompi;

import java.util.Optional;

/** Puerto hacia la API REST de Wompi (permite cambiar la implementacion o simularla en pruebas). */
public interface ClienteWompi {

    /**
     * Consulta la transaccion en Wompi con la llave privada.
     *
     * @throws unisimon.demo.exception.PasarelaNoDisponibleException si Wompi no responde o responde con error.
     */
    TransaccionWompi consultarTransaccion(String idTransaccion);

    /**
     * Busca la transaccion de un pago por su referencia (la que el backend genero al iniciar el checkout).
     * Vacio si el residente nunca llego a pagar en Wompi. Si hay varios intentos, prefiere el aprobado.
     *
     * @throws unisimon.demo.exception.PasarelaNoDisponibleException si Wompi no responde o responde con error.
     */
    Optional<TransaccionWompi> buscarPorReferencia(String referencia);
}
