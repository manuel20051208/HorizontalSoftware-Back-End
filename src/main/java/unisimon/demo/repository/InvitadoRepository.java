package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.UsuarioAutorizado;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Invitados (UsuarioAutorizado) y su relacion con los residentes que los autorizan. SQL nativo. */
public interface InvitadoRepository extends Repository<UsuarioAutorizado, Long> {

    interface InvitadoView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        String getCorreoElectronico();

        Boolean getActive();

        LocalDate getVenceAutorizacion();

        /** Inmuebles del invitado como "id|direccion;id|direccion" (vacio o null si no tiene). */
        String getInmuebles();
    }

    /** Residente que autorizo a un invitado. */
    interface AnfitrionView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        /** Inmuebles del anfitrion como "id|direccion;id|direccion" (vacio o null si no tiene). */
        String getInmuebles();
    }

    String SUB_INMUEBLES = """
            (SELECT string_agg(i.id || '|' || i.direccion_inmueble, ';' ORDER BY i.direccion_inmueble)
             FROM usuario_inmuebles ui
             JOIN inmuebles i ON i.id = ui.inmueble_id
             WHERE ui.usuario_id = u.id)
            """;

    String SELECT_INVITADO = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.active AS "active",
                   u.vence_autorizacion AS "venceAutorizacion", """ + SUB_INMUEBLES + """
                   AS "inmuebles"
            FROM usuarios u
            """;

    @Modifying
    @Query(value = """
            INSERT INTO residente_invitados (residente_id, invitado_id) VALUES (:residenteId, :invitadoId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int vincular(@Param("residenteId") Long residenteId, @Param("invitadoId") Long invitadoId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM residente_invitados
                           WHERE residente_id = :residenteId AND invitado_id = :invitadoId)
            """, nativeQuery = true)
    boolean esInvitadoDe(@Param("residenteId") Long residenteId, @Param("invitadoId") Long invitadoId);

    /** Invitados del residente en el inmueble elegido: la puente usuario_inmuebles filtra el listado. */
    @Query(value = SELECT_INVITADO + """
            JOIN residente_invitados ri ON ri.invitado_id = u.id
            JOIN usuario_inmuebles ui ON ui.usuario_id = u.id
            WHERE ri.residente_id = :residenteId AND ui.inmueble_id = :inmuebleId
            ORDER BY u.nombre_completo, u.id
            """ + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<InvitadoView> listarDeResidenteEnInmueble(@Param("residenteId") Long residenteId,
                                                   @Param("inmuebleId") Long inmuebleId,
                                                   @Param("limite") int limite,
                                                   @Param("desplazamiento") long desplazamiento);

    /** El mismo filtro que el listado para que el total de la pagina cuente solo ese inmueble. */
    @Query(value = """
            SELECT COUNT(*) FROM residente_invitados ri
            JOIN usuario_inmuebles ui ON ui.usuario_id = ri.invitado_id
            WHERE ri.residente_id = :residenteId AND ui.inmueble_id = :inmuebleId
            """, nativeQuery = true)
    long contarDeResidenteEnInmueble(@Param("residenteId") Long residenteId,
                                     @Param("inmuebleId") Long inmuebleId);

    // ------------------------------------------------------------------ relacion inmueble <-> residente

    /** Invitados cuyo unico anfitrion es este residente: se mudan con el o se desactivan con el. */
    @Query(value = """
            SELECT ri.invitado_id FROM residente_invitados ri
            WHERE ri.residente_id = :residenteId
              AND NOT EXISTS (SELECT 1 FROM residente_invitados otro
                              WHERE otro.invitado_id = ri.invitado_id AND otro.residente_id <> :residenteId)
            ORDER BY ri.invitado_id
            """, nativeQuery = true)
    List<Long> listarExclusivosDeResidente(@Param("residenteId") Long residenteId);

    /**
     * Ids de los invitados autorizados por el residente que, ademas, estan relacionados con ese inmueble. Asi la
     * mudanza X -> Y no arrastra a un invitado que solo compartia otro inmueble Z del residente.
     */
    @Query(value = """
            SELECT ri.invitado_id FROM residente_invitados ri
            JOIN usuario_inmuebles ui ON ui.usuario_id = ri.invitado_id
            WHERE ri.residente_id = :residenteId AND ui.inmueble_id = :inmuebleId
            ORDER BY ri.invitado_id
            """, nativeQuery = true)
    List<Long> listarIdsDeResidenteEnInmueble(@Param("residenteId") Long residenteId,
                                              @Param("inmuebleId") Long inmuebleId);

    /** Agrega a los invitados el vinculo con el inmueble (idempotente). */
    @Modifying
    @Query(value = """
            INSERT INTO usuario_inmuebles (usuario_id, inmueble_id)
            SELECT id, :inmuebleId FROM usuarios WHERE id IN (:ids)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int vincularAInmueble(@Param("ids") List<Long> ids, @Param("inmuebleId") Long inmuebleId);

    /** Quita a los invitados el vinculo con ese inmueble (los demas vinculos siguen). */
    @Modifying
    @Query(value = "DELETE FROM usuario_inmuebles WHERE inmueble_id = :inmuebleId AND usuario_id IN (:ids)",
            nativeQuery = true)
    int desvincularDeInmueble(@Param("ids") List<Long> ids, @Param("inmuebleId") Long inmuebleId);

    /** Desactiva invitados; conservan los inmuebles que tenian. */
    @Modifying
    @Query(value = "UPDATE usuarios SET active = FALSE WHERE id IN (:ids)", nativeQuery = true)
    int desactivar(@Param("ids") List<Long> ids);

    /**
     * Otro anfitrion (distinto de {@code residenteId}) del invitado sigue relacionado con el inmueble: entonces el
     * invitado conserva ese inmueble al desvincular a este residente.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM residente_invitados ri
                           JOIN usuario_inmuebles ui ON ui.usuario_id = ri.residente_id
                           WHERE ri.invitado_id = :invitadoId AND ri.residente_id <> :residenteId
                             AND ui.inmueble_id = :inmuebleId)
            """, nativeQuery = true)
    boolean tieneOtroAnfitrionEnInmueble(@Param("invitadoId") Long invitadoId,
                                         @Param("residenteId") Long residenteId,
                                         @Param("inmuebleId") Long inmuebleId);

    /** Vigilante.consultarInvitados(): una pagina de todos los invitados registrados. */
    @Query(value = SELECT_INVITADO + """
            WHERE u.tipo_usuario = 'USUARIO_AUTORIZADO'
            ORDER BY u.nombre_completo, u.id
            """ + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<InvitadoView> listarTodos(@Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM usuarios WHERE tipo_usuario = 'USUARIO_AUTORIZADO'", nativeQuery = true)
    long contarTodos();

    @Query(value = SELECT_INVITADO + "WHERE u.tipo_usuario = 'USUARIO_AUTORIZADO' AND u.identificacion = :identificacion",
            nativeQuery = true)
    Optional<InvitadoView> buscarPorIdentificacion(@Param("identificacion") Long identificacion);

    @Query(value = SELECT_INVITADO + "WHERE u.tipo_usuario = 'USUARIO_AUTORIZADO' AND u.id = :id", nativeQuery = true)
    Optional<InvitadoView> buscarPorId(@Param("id") Long id);

    String SELECT_ANFITRION = """
            SELECT r.id AS "id", r.identificacion AS "identificacion", r.nombre_completo AS "nombreCompleto",
                   (SELECT string_agg(i.id || '|' || i.direccion_inmueble, ';' ORDER BY i.direccion_inmueble)
                    FROM usuario_inmuebles ui
                    JOIN inmuebles i ON i.id = ui.inmueble_id
                    WHERE ui.usuario_id = r.id) AS "inmuebles"
            FROM residente_invitados ri
            JOIN usuarios r ON r.id = ri.residente_id
            WHERE ri.invitado_id = :invitadoId
            """;

    /** Los residentes que autorizaron a este invitado (todos: el vigilante avisa a cada inmueble). */
    @Query(value = SELECT_ANFITRION + "ORDER BY r.nombre_completo", nativeQuery = true)
    List<AnfitrionView> listarAnfitriones(@Param("invitadoId") Long invitadoId);

    /** buscarRelacionResidente(): una pagina de los residentes que autorizaron a este invitado. */
    @Query(value = SELECT_ANFITRION + "ORDER BY r.nombre_completo, r.id" + SqlPaginacion.LIMITE_DESPLAZAMIENTO,
            nativeQuery = true)
    List<AnfitrionView> listarAnfitriones(@Param("invitadoId") Long invitadoId,
                                          @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM residente_invitados WHERE invitado_id = :invitadoId", nativeQuery = true)
    long contarAnfitriones(@Param("invitadoId") Long invitadoId);

    /** Residente ◇— UsuarioAutorizado es agregacion: se borra el vinculo, el invitado sigue existiendo. */
    @Modifying
    @Query(value = "DELETE FROM residente_invitados WHERE residente_id = :residenteId AND invitado_id = :invitadoId",
            nativeQuery = true)
    int desvincular(@Param("residenteId") Long residenteId, @Param("invitadoId") Long invitadoId);

    /** Quita todos los vinculos de la cuenta, como anfitrion o como invitado (antes de borrarla). */
    @Modifying
    @Query(value = "DELETE FROM residente_invitados WHERE residente_id = :usuarioId OR invitado_id = :usuarioId",
            nativeQuery = true)
    int desvincularTodos(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query(value = """
            UPDATE usuarios SET nombre_completo = :nombre, fecha_nacimiento = :fechaNacimiento,
                                vence_autorizacion = :vence
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarDatos(@Param("id") Long id, @Param("nombre") String nombre,
                        @Param("fechaNacimiento") LocalDate fechaNacimiento, @Param("vence") LocalDate vence);
}
