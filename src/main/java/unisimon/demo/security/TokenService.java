package unisimon.demo.security;

/** Puerto para emitir y leer tokens. Los servicios de negocio dependen de esta interfaz, no de Nimbus/JWT. */
public interface TokenService {

    /** Emite un access token (corto) y un refresh token (largo) para el usuario. */
    ParTokens emitir(IdentidadToken identidad);

    /**
     * Valida un refresh token (firma, emisor, vencimiento y tipo) y devuelve a quien pertenece.
     *
     * @throws unisimon.demo.exception.NoAutenticadoException si no es valido
     */
    IdentidadToken leerRefresh(String refreshToken);

    /** Huella SHA-256 (hex) del token: es lo unico que se guarda en base de datos. */
    String huella(String token);
}
