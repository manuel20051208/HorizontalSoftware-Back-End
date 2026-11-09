package unisimon.demo.model;

/**
 * &lt;&lt;Interface&gt;&gt; NoAdmin (diagrama de clases).
 * Marca a los usuarios que NO son administradores: {@link Residente}, {@link Vigilante}
 * y {@link UsuarioAutorizado}. El administrador los gestiona con
 * {@code AdministradorService.modificarActive(...)}, que devuelve un NoAdmin.
 */
public interface NoAdmin {
}
