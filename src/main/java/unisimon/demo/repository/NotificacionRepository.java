package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Notificacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Notificaciones y sus destinatarios. SQL nativo. */
public interface NotificacionRepository extends Repository<Notificacion, Long> {

    interface NotificacionView {
        Long getId();

        String getMensaje();

        String getTipoNotificacion();

        LocalDateTime getFechaEnvio();
    }

    /** Quien ya leyo (usuarios.notificacion_leida) entre los destinatarios de una notificacion. */
    interface LecturaView {
        Long getUsuarioId();

        String getNombreCompleto();

        Boolean getLeida();
    }

    @Query(value = """
            INSERT INTO notificaciones (mensaje, tipo_notificacion, fecha_envio)
            VALUES (:#{#n.mensaje}, :#{#n.tipoNotificacion}, :#{#n.fechaEnvio})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("n") Notificacion n);

    @Modifying
    @Query(value = """
            INSERT INTO notificacion_usuarios (notificacion_id, usuario_id)
            SELECT :notificacionId, u.id FROM usuarios u WHERE u.id IN (:usuarioIds)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int agregarDestinatarios(@Param("notificacionId") Long notificacionId, @Param("usuarioIds") List<Long> usuarioIds);

    @Modifying
    @Query(value = """
            INSERT INTO inmueble_notificaciones (inmueble_id, notificacion_id) VALUES (:inmuebleId, :notificacionId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int vincularInmueble(@Param("inmuebleId") Long inmuebleId, @Param("notificacionId") Long notificacionId);

    @Query(value = """
            SELECT n.id AS "id", n.mensaje AS "mensaje", n.tipo_notificacion AS "tipoNotificacion",
                   n.fecha_envio AS "fechaEnvio"
            FROM notificaciones n
            JOIN notificacion_usuarios nu ON nu.notificacion_id = n.id
            WHERE nu.usuario_id = :usuarioId
            ORDER BY n.fecha_envio DESC, n.id DESC
            """ + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<NotificacionView> listarDeUsuario(@Param("usuarioId") Long usuarioId,
                                           @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM notificacion_usuarios WHERE usuario_id = :usuarioId", nativeQuery = true)
    long contarDeUsuario(@Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT n.id AS "id", n.mensaje AS "mensaje", n.tipo_notificacion AS "tipoNotificacion",
                   n.fecha_envio AS "fechaEnvio"
            FROM notificaciones n
            WHERE n.tipo_notificacion = :tipo
            ORDER BY n.fecha_envio DESC, n.id DESC
            """ + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<NotificacionView> listarPorTipo(@Param("tipo") String tipo,
                                         @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM notificaciones WHERE tipo_notificacion = :tipo", nativeQuery = true)
    long contarPorTipo(@Param("tipo") String tipo);

    @Query(value = """
            SELECT u.id AS "usuarioId", u.nombre_completo AS "nombreCompleto", u.notificacion_leida AS "leida"
            FROM notificacion_usuarios nu
            JOIN usuarios u ON u.id = nu.usuario_id
            WHERE nu.notificacion_id = :notificacionId
            ORDER BY u.nombre_completo
            """, nativeQuery = true)
    List<LecturaView> listarLectura(@Param("notificacionId") Long notificacionId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM notificaciones WHERE id = :id)", nativeQuery = true)
    boolean existePorId(@Param("id") Long id);

    // ------------------------------------------------------------------ consulta general y borrado

    String SELECT_NOTIFICACION = """
            SELECT n.id AS "id", n.mensaje AS "mensaje", n.tipo_notificacion AS "tipoNotificacion",
                   n.fecha_envio AS "fechaEnvio"
            FROM notificaciones n
            """;

    @Query(value = SELECT_NOTIFICACION + "ORDER BY n.fecha_envio DESC, n.id DESC" + SqlPaginacion.LIMITE_DESPLAZAMIENTO,
            nativeQuery = true)
    List<NotificacionView> listar(@Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM notificaciones", nativeQuery = true)
    long contar();

    @Query(value = SELECT_NOTIFICACION + "WHERE n.id = :id", nativeQuery = true)
    Optional<NotificacionView> buscarPorId(@Param("id") Long id);

    /** Notificacion ◇— Usuario es agregacion: se borran los vinculos, los usuarios siguen existiendo. */
    @Modifying
    @Query(value = "DELETE FROM notificacion_usuarios WHERE notificacion_id = :id", nativeQuery = true)
    int desvincularUsuarios(@Param("id") Long id);

    /** Inmueble ◇— Notificacion es agregacion: se borran los vinculos, los inmuebles siguen existiendo. */
    @Modifying
    @Query(value = "DELETE FROM inmueble_notificaciones WHERE notificacion_id = :id", nativeQuery = true)
    int desvincularInmuebles(@Param("id") Long id);

    /** El usuario deja de ser destinatario de todas sus notificaciones (antes de borrar su cuenta). */
    @Modifying
    @Query(value = "DELETE FROM notificacion_usuarios WHERE usuario_id = :usuarioId", nativeQuery = true)
    int quitarDestinatario(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query(value = "DELETE FROM notificaciones WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
