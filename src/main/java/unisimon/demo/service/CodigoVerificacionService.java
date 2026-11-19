package unisimon.demo.service;

/** Codigo de 4 cifras que llega al correo del usuario y que el administrador confirma para verificarlo. */
public interface CodigoVerificacionService {

    /** Genera un codigo nuevo para el usuario (reemplaza el anterior y reinicia los intentos) y lo envia a su correo. */
    void emitir(Long usuarioId, String correo);

    /**
     * Comprueba el codigo. Si es correcto lo consume; si no, suma un intento fallido.
     *
     * @throws unisimon.demo.exception.SolicitudInvalidaException si el codigo es incorrecto
     * @throws unisimon.demo.exception.ConflictoException         si no hay codigo, vencio o se agotaron los intentos
     */
    void verificar(Long usuarioId, String codigo);
}
