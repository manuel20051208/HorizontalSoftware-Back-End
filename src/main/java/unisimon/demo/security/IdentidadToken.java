package unisimon.demo.security;

/** Datos del usuario que viajan firmados dentro del token. */
public record IdentidadToken(Long usuarioId, String rol, String nombre) {
}
