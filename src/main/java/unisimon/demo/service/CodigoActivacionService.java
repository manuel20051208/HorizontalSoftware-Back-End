package unisimon.demo.service;

/** Codigo de 4 cifras que llega por correo y que se exige para activar una cuenta. */
public interface CodigoActivacionService {

    /** Genera un codigo nuevo para el usuario (reemplaza el anterior) y lo envia a su correo. */
    void emitir(Long usuarioId, String correo);

    /**
     * Comprueba el codigo. Si es correcto lo consume; si no, suma un intento fallido.
     *
     * @throws unisimon.demo.exception.NoAutenticadoException si el codigo es incorrecto
     * @throws unisimon.demo.exception.ProhibidoException     si no hay codigo, vencio o se agotaron los intentos
     */
    void verificar(Long usuarioId, String codigo);
}
