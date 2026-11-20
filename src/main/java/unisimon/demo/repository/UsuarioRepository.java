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
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;

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

        /** Inmuebles de la cuenta como "id|direccion;id|direccion" (vacio o null si no tiene). */
        String getInmuebles();

        Boolean getNotificacionLeida();

        LocalDate getFechaCreacionCuenta();

        /** TRUE cuando el administrador confirmo el codigo enviado al correo (V9). */
        Boolean getCorreoVerificado();

        /** Para prellenar el formulario de editar la cuenta. */
        LocalDate getFechaNacimiento();

        LocalDate getInicioContrato();

        LocalDate getVenceContrato();

        LocalDate getVenceAutorizacion();
    }

    /** Fila para listados de usuarios. */
    interface UsuarioListaView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        String getCorreoElectronico();

        String getTipoUsuario();

        Boolean getActive();

        /** Inmuebles de la cuenta como "id|direccion;id|direccion" (vacio o null si no tiene). */
        String getInmuebles();

        LocalDate getFechaCreacionCuenta();

        LocalDate getVenceAutorizacion();

        Boolean getCorreoVerificado();

        LocalDate getFechaNacimiento();

        LocalDate getInicioContrato();

        LocalDate getVenceContrato();
    }

    /** Persona relacionada con un inmueble (residente o invitado). */
    interface UsuarioDeInmuebleView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        String getTipoUsuario();

        Boolean getActive();
    }

    /** Lo necesario para Usuario.configurarPreferencias: si la cuenta esta activa y sus preferencias actuales. */
    interface PreferenciasView {
        Boolean getActive();

        /** Tipos separados por coma; vacio si no ha configurado ninguno. */
        String getPreferencias();
    }

    /** Inmuebles de la cuenta como texto, para no repetir la subconsulta en cada listado. */
    String SUB_INMUEBLES = """
            (SELECT string_agg(i.id || '|' || i.direccion_inmueble, ';' ORDER BY i.direccion_inmueble)
             FROM usuario_inmuebles ui
             JOIN inmuebles i ON i.id = ui.inmueble_id
             WHERE ui.usuario_id = u.id)
            """;

    String SELECT_CREDENCIALES = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.contrasena AS "contrasena",
                   u.tipo_usuario AS "tipoUsuario", u.active AS "active", u.token AS "token",
                   """ + SUB_INMUEBLES + """
                   AS "inmuebles", u.notificacion_leida AS "notificacionLeida",
                   u.fecha_creacion_cuenta AS "fechaCreacionCuenta", u.correo_verificado AS "correoVerificado",
                   u.fecha_nacimiento AS "fechaNacimiento",
                   COALESCE(u.admin_inicio_contrato, u.vigilante_inicio_contrato) AS "inicioContrato",
                   COALESCE(u.admin_vence_contrato, u.vigilante_vence_contrato) AS "venceContrato",
                   u.vence_autorizacion AS "venceAutorizacion"
            FROM usuarios u
            """;

    String SELECT_LISTA = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.tipo_usuario AS "tipoUsuario",
                   u.active AS "active", """ + SUB_INMUEBLES + """
                   AS "inmuebles",
                   u.fecha_creacion_cuenta AS "fechaCreacionCuenta", u.vence_autorizacion AS "venceAutorizacion",
                   u.correo_verificado AS "correoVerificado",
                   u.fecha_nacimiento AS "fechaNacimiento",
                   COALESCE(u.admin_inicio_contrato, u.vigilante_inicio_contrato) AS "inicioContrato",
                   COALESCE(u.admin_vence_contrato, u.vigilante_vence_contrato) AS "venceContrato"
            FROM usuarios u
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

    String FILTRO_LISTA = """
            WHERE (CAST(:tipo AS VARCHAR) IS NULL OR u.tipo_usuario = CAST(:tipo AS VARCHAR))
              AND (CAST(:active AS BOOLEAN) IS NULL OR u.active = CAST(:active AS BOOLEAN))
            """;

    @Query(value = SELECT_LISTA + FILTRO_LISTA + "ORDER BY u.nombre_completo, u.id"
            + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<UsuarioListaView> listar(@Param("tipo") String tipo, @Param("active") Boolean active,
                                  @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM usuarios u " + FILTRO_LISTA, nativeQuery = true)
    long contar(@Param("tipo") String tipo, @Param("active") Boolean active);

    @Query(value = SELECT_LISTA + """
            JOIN usuario_inmuebles ui ON ui.usuario_id = u.id
            WHERE ui.inmueble_id = :inmuebleId
            ORDER BY u.nombre_completo
            """, nativeQuery = true)
    List<UsuarioListaView> listarPorInmueble(@Param("inmuebleId") Long inmuebleId);

    /** Residentes e invitados relacionados con el inmueble, por tipo y nombre. */
    @Query(value = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.tipo_usuario AS "tipoUsuario", u.active AS "active"
            FROM usuario_inmuebles ui
            JOIN usuarios u ON u.id = ui.usuario_id
            WHERE ui.inmueble_id = :inmuebleId
            ORDER BY u.tipo_usuario, u.nombre_completo, u.id
            """, nativeQuery = true)
    List<UsuarioDeInmuebleView> listarUsuariosDeInmueble(@Param("inmuebleId") Long inmuebleId);

    /** Inmuebles de una cuenta (para el selector del residente y para hidratar sus respuestas). */
    @Query(value = """
            SELECT i.id AS "id", i.direccion_inmueble AS "direccionInmueble", i.is_active AS "isActive",
                   i.liquidacion_actual_id AS "liquidacionActualId"
            FROM usuario_inmuebles ui
            JOIN inmuebles i ON i.id = ui.inmueble_id
            WHERE ui.usuario_id = :usuarioId
            ORDER BY i.direccion_inmueble
            """, nativeQuery = true)
    List<InmuebleBasicoView> listarInmueblesDe(@Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM usuario_inmuebles WHERE usuario_id = :usuarioId", nativeQuery = true)
    long contarInmueblesDe(@Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM usuario_inmuebles
                           WHERE usuario_id = :usuarioId AND inmueble_id = :inmuebleId)
            """, nativeQuery = true)
    boolean estaRelacionado(@Param("usuarioId") Long usuarioId, @Param("inmuebleId") Long inmuebleId);

    @Query(value = """
            SELECT u.active AS "active", array_to_string(u.preferencias_notificacion, ',') AS "preferencias"
            FROM usuarios u WHERE u.id = :id
            """, nativeQuery = true)
    Optional<PreferenciasView> buscarPreferencias(@Param("id") Long id);

    /** De los usuarios dados, los que quieren recibir ese tipo de notificacion (sin preferencias = todos). */
    @Query(value = """
            SELECT u.id FROM usuarios u
            WHERE u.id IN (:ids)
              AND (cardinality(u.preferencias_notificacion) = 0 OR :tipo = ANY (u.preferencias_notificacion))
            """, nativeQuery = true)
    List<Long> filtrarPorPreferencia(@Param("ids") List<Long> ids, @Param("tipo") String tipo);

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
                                  contrasena, fecha_creacion_cuenta, active, notificacion_leida)
            VALUES (:#{#r.tipoUsuario}, :#{#r.identificacion}, :#{#r.nombreCompleto}, :#{#r.fechaNacimiento},
                    :#{#r.correoElectronico}, :#{#r.contrasena}, :#{#r.fechaCreacionCuenta}, :#{#r.active},
                    :#{#r.notificacionLeida})
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
                                  vence_autorizacion)
            VALUES (:#{#u.tipoUsuario}, :#{#u.identificacion}, :#{#u.nombreCompleto}, :#{#u.fechaNacimiento},
                    :#{#u.correoElectronico}, :#{#u.contrasena}, :#{#u.fechaCreacionCuenta}, :#{#u.active},
                    :#{#u.notificacionLeida}, :#{#u.venceAutorizacion})
            RETURNING id
            """, nativeQuery = true)
    Long insertarInvitado(@Param("u") UsuarioAutorizado u);

    // ------------------------------------------------------------------ cambios por columna

    @Modifying
    @Query(value = "UPDATE usuarios SET active = :active WHERE id = :id", nativeQuery = true)
    int actualizarActive(@Param("id") Long id, @Param("active") boolean active);

    /** El administrador confirmo el codigo de 4 cifras enviado al correo de este usuario. */
    @Modifying
    @Query(value = "UPDATE usuarios SET correo_verificado = TRUE WHERE id = :id", nativeQuery = true)
    int marcarCorreoVerificado(@Param("id") Long id);

    /** Guarda (o borra con null) la huella del refresh token vigente: es la sesion del usuario. */
    @Modifying
    @Query(value = "UPDATE usuarios SET token = :token WHERE id = :id", nativeQuery = true)
    int actualizarToken(@Param("id") Long id, @Param("token") String token);

    /** Agrega el vinculo cuenta <-> inmueble (idempotente). */
    @Modifying
    @Query(value = """
            INSERT INTO usuario_inmuebles (usuario_id, inmueble_id) VALUES (:usuarioId, :inmuebleId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int vincularInmueble(@Param("usuarioId") Long usuarioId, @Param("inmuebleId") Long inmuebleId);

    /** Quita el vinculo cuenta <-> inmueble; el usuario y el inmueble siguen existiendo. */
    @Modifying
    @Query(value = "DELETE FROM usuario_inmuebles WHERE usuario_id = :usuarioId AND inmueble_id = :inmuebleId",
            nativeQuery = true)
    int desvincularInmueble(@Param("usuarioId") Long usuarioId, @Param("inmuebleId") Long inmuebleId);

    @Modifying
    @Query(value = "UPDATE usuarios SET contrasena = :contrasena WHERE id = :id", nativeQuery = true)
    int actualizarContrasena(@Param("id") Long id, @Param("contrasena") String contrasena);

    @Modifying
    @Query(value = "UPDATE usuarios SET notificacion_leida = :leida WHERE id = :id", nativeQuery = true)
    int actualizarNotificacionLeida(@Param("id") Long id, @Param("leida") boolean leida);

    /** preferencias: tipos separados por coma ("" deja la lista vacia). */
    @Modifying
    @Query(value = """
            UPDATE usuarios SET preferencias_notificacion = CAST(string_to_array(:preferencias, ',') AS VARCHAR(30)[])
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarPreferencias(@Param("id") Long id, @Param("preferencias") String preferencias);

    @Modifying
    @Query(value = "UPDATE usuarios SET notificacion_leida = FALSE WHERE id IN (:ids)", nativeQuery = true)
    int marcarNoLeida(@Param("ids") List<Long> ids);

    // ------------------------------------------------------------------ actualizar y eliminar

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM usuarios WHERE LOWER(correo_electronico) = LOWER(:correo) AND id <> :id)
            """, nativeQuery = true)
    boolean existePorCorreoEnOtro(@Param("correo") String correo, @Param("id") Long id);

    @Modifying
    @Query(value = """
            UPDATE usuarios SET nombre_completo = :nombre, fecha_nacimiento = :fechaNacimiento,
                                correo_electronico = :correo
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarDatos(@Param("id") Long id, @Param("nombre") String nombre,
                        @Param("fechaNacimiento") LocalDate fechaNacimiento, @Param("correo") String correo);

    @Modifying
    @Query(value = "UPDATE usuarios SET admin_inicio_contrato = :inicio, admin_vence_contrato = :vence WHERE id = :id",
            nativeQuery = true)
    int actualizarContratoAdministrador(@Param("id") Long id, @Param("inicio") LocalDate inicio,
                                        @Param("vence") LocalDate vence);

    @Modifying
    @Query(value = """
            UPDATE usuarios SET vigilante_inicio_contrato = :inicio, vigilante_vence_contrato = :vence WHERE id = :id
            """, nativeQuery = true)
    int actualizarContratoVigilante(@Param("id") Long id, @Param("inicio") LocalDate inicio,
                                    @Param("vence") LocalDate vence);

    @Modifying
    @Query(value = "UPDATE usuarios SET vence_autorizacion = :vence WHERE id = :id", nativeQuery = true)
    int actualizarVenceAutorizacion(@Param("id") Long id, @Param("vence") LocalDate vence);

    /** Un correo nuevo hay que verificarlo otra vez: la cuenta queda inactiva y sin sesion. */
    @Modifying
    @Query(value = "UPDATE usuarios SET correo_verificado = FALSE, active = FALSE, token = NULL WHERE id = :id",
            nativeQuery = true)
    int reiniciarVerificacion(@Param("id") Long id);

    /**
     * Borra la cuenta. La base borra en cascada sus partes por composicion (Residente ◆— Auto y
     * Residente ◆— ReservaZonaComun), el vinculo con sus inmuebles y su codigo de verificacion.
     */
    @Modifying
    @Query(value = "DELETE FROM usuarios WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
