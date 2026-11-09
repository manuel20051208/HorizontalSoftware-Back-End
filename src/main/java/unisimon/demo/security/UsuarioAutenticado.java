package unisimon.demo.security;

/** Quien hace la peticion, ya validado por el token. Se inyecta en los controladores como parametro. */
public record UsuarioAutenticado(Long id, String rol) {
}
