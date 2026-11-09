package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.model.Vigilante;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla usuarios (cuentas). Solo SQL nativo de PostgreSQL: lecturas con proyecciones y
 * escrituras por columna (UPDATE ... SET col), sin cargar nunca la entidad completa.
 */
public interface UsuarioRepository extends Repository<Usuario, Long> {

    /** Lo necesario para autenticar y para decidir permisos. */
    interface CredencialesView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        String getCorreoElectronico();

        String getContrasena();

        String getTipoUsuario();

        Boolean getActive();

        String getToken();

        Long getInmuebleId();

        Boolean getNotificacionLeida();

        LocalDate getFechaCreacionCuenta();
    }

    /** Fila para listados de usuarios. */
    interface UsuarioListaView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        String getCorreoElectronico();

        String getTipoUsuario();

        Boolean getActive();

        Long getInmuebleId();

        String getDireccionInmueble();

        LocalDate getFechaCreacionCuenta();

        LocalDate getVenceAutorizacion();
    }

    String SELECT_CREDENCIALES = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.contrasena AS "contrasena",
                   u.tipo_usuario AS "tipoUsuario", u.active AS "active", u.token AS "token",
                   u.inmueble_id AS "inmuebleId", u.notificacion_leida AS "notificacionLeida",
                   u.fecha_creacion_cuenta AS "fechaCreacionCuenta"
            FROM usuarios u
            """;

    String SELECT_LISTA = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.tipo_usuario AS "tipoUsuario",
                   u.active AS "active", u.inmueble_id AS "inmuebleId",
                   i.direccion_inmueble AS "direccionInmueble",
                   u.fecha_creacion_cuenta AS "fechaCreacionCuenta", u.vence_autorizacion AS "venceAutorizacion"
            FROM usuarios u
            LEFT JOIN inmuebles i ON i.id = u.inmueble_id
            """;

    // ------------------------------------------------------------------ lecturas

    @Query(value = SELECT_CREDENCIALES + "WHERE LOWER(u.correo_electronico) = LOWER(:correo)", nativeQuery = true)
    Optional<CredencialesView> buscarCredencialesPorCorreo(@Param("correo") String correo);

    @Query(value = SELECT_CREDENCIALES + "WHERE u.id = :id", nativeQuery = true)
    Optional<CredencialesView> buscarCredencialesPorId(@Param("id") Long id);

    @Query(value = SELECT_CREDENCIALES + "WHERE u.identificacion = :identificacion", nativeQuery = true)
    Optional<CredencialesView> buscarCredencialesPorIdentificacion(@Param("identificacion") Long identificacion);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM usuarios WHERE LOWER(correo_electronico) = LOWER(:correo))",
            nativeQuery = true)
    boolean existePorCorreo(@Param("correo") String correo);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM usuarios WHERE identificacion = :identificacion)", nativeQuery = true)
    boolean existePorIdentificacion(@Param("identificacion") Long identificacion);

    @Query(value = SELECT_LISTA + """
            WHERE (CAST(:tipo AS VARCHAR) IS NULL OR u.tipo_usuario = CAST(:tipo AS VARCHAR))
              AND (CAST(:active AS BOOLEAN) IS NULL OR u.active = CAST(:active AS BOOLEAN))
            ORDER BY u.nombre_completo
            """, nativeQuery = true)
    List<UsuarioListaView> listar(@Param("tipo") String tipo, @Param("active") Boolean active);

    @Query(value = SELECT_LISTA + "WHERE u.inmueble_id = :inmuebleId ORDER BY u.nombre_completo", nativeQuery = true)
    List<UsuarioListaView> listarPorInmueble(@Param("inmuebleId") Long inmuebleId);

    // ------------------------------------------------------------------ altas (INSERT ... RETURNING id)

    @Query(value = """
            INSERT INTO usuarios (tipo_usuario, identificacion, nombre_completo, fecha_nacimiento, correo_electronico,
                                  contrasena, fecha_creacion_cuenta, active, notificacion_leida,
                                  admin_inicio_contrato, admin_vence_contrato)
            VALUES (:#{#a.tipoUsuario}, :#{#a.identificacion}, :#{#a.nombreCompleto}, :#{#a.fechaNacimiento},
                    :#{#a.correoElectronico}, :#{#a.contrasena}, :#{#a.fechaCreacionCuenta}, :#{#a.active},
                    :#{#a.notificacionLeida}, :#{#a.inicioContrato}, :#{#a.venceContrato})
            RETURNING id
            """, nativeQuery = true)
    Long insertarAdministrador(@Param("a") Administrador a);

    @Query(value = """
            INSERT INTO usuarios (tipo_usuario, identificacion, nombre_completo, fecha_nacimiento, correo_electronico,
                                  contrasena, fecha_creacion_cuenta, active, notificacion_leida, inmueble_id)
            VALUES (:#{#r.tipoUsuario}, :#{#r.identificacion}, :#{#r.nombreCompleto}, :#{#r.fechaNacimiento},
                    :#{#r.correoElectronico}, :#{#r.contrasena}, :#{#r.fechaCreacionCuenta}, :#{#r.active},
                    :#{#r.notificacionLeida}, :#{#r.inmueble.id})
            RETURNING id
            """, nativeQuery = true)
    Long insertarResidente(@Param("r") Residente r);

    @Query(value = """
            INSERT INTO usuarios (tipo_usuario, identificacion, nombre_completo, fecha_nacimiento, correo_electronico,
                                  contrasena, fecha_creacion_cuenta, active, notificacion_leida,
                                  vigilante_inicio_contrato, vigilante_vence_contrato)
            VALUES (:#{#v.tipoUsuario}, :#{#v.identificacion}, :#{#v.nombreCompleto}, :#{#v.fechaNacimiento},
                    :#{#v.correoElectronico}, :#{#v.contrasena}, :#{#v.fechaCreacionCuenta}, :#{#v.active},
                    :#{#v.notificacionLeida}, :#{#v.inicioContrato}, :#{#v.venceContrato})
            RETURNING id
            """, nativeQuery = true)
    Long insertarVigilante(@Param("v") Vigilante v);

    @Query(value = """
            INSERT INTO usuarios (tipo_usuario, identificacion, nombre_completo, fecha_nacimiento, correo_electronico,
                                  contrasena, fecha_creacion_cuenta, active, notificacion_leida,
                                  inmueble_id, vence_autorizacion)
            VALUES (:#{#u.tipoUsuario}, :#{#u.identificacion}, :#{#u.nombreCompleto}, :#{#u.fechaNacimiento},
                    :#{#u.correoElectronico}, :#{#u.contrasena}, :#{#u.fechaCreacionCuenta}, :#{#u.active},
                    :#{#u.notificacionLeida}, :#{#u.inmueble.id}, :#{#u.venceAutorizacion})
            RETURNING id
            """, nativeQuery = true)
    Long insertarInvitado(@Param("u") UsuarioAutorizado u);

    // ------------------------------------------------------------------ cambios por columna

    @Modifying
    @Query(value = "UPDATE usuarios SET active = :active WHERE id = :id", nativeQuery = true)
    int actualizarActive(@Param("id") Long id, @Param("active") boolean active);

    /** Guarda (o borra con null) la huella del refresh token vigente: es la sesion del usuario. */
    @Modifying
    @Query(value = "UPDATE usuarios SET token = :token WHERE id = :id", nativeQuery = true)
    int actualizarToken(@Param("id") Long id, @Param("token") String token);

    @Modifying
    @Query(value = "UPDATE usuarios SET contrasena = :contrasena WHERE id = :id", nativeQuery = true)
    int actualizarContrasena(@Param("id") Long id, @Param("contrasena") String contrasena);

    @Modifying
    @Query(value = "UPDATE usuarios SET notificacion_leida = :leida WHERE id = :id", nativeQuery = true)
    int actualizarNotificacionLeida(@Param("id") Long id, @Param("leida") boolean leida);

    @Modifying
    @Query(value = "UPDATE usuarios SET notificacion_leida = FALSE WHERE id IN (:ids)", nativeQuery = true)
    int marcarNoLeida(@Param("ids") List<Long> ids);
}
