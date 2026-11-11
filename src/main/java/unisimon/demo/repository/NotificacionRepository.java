package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Notificacion;

import java.time.LocalDateTime;
import java.util.List;

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
            SELECT u.id AS "usuarioId", u.nombre_completo AS "nombreCompleto", u.notificacion_leida AS "leida"
            FROM notificacion_usuarios nu
            JOIN usuarios u ON u.id = nu.usuario_id
            WHERE nu.notificacion_id = :notificacionId
            ORDER BY u.nombre_completo
            """, nativeQuery = true)
    List<LecturaView> listarLectura(@Param("notificacionId") Long notificacionId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM notificaciones WHERE id = :id)", nativeQuery = true)
    boolean existePorId(@Param("id") Long id);
}
