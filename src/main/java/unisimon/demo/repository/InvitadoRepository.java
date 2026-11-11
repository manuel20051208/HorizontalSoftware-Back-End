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

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    /** Residente que autorizo a un invitado. */
    interface AnfitrionView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    String SELECT_INVITADO = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.correo_electronico AS "correoElectronico", u.active AS "active",
                   u.vence_autorizacion AS "venceAutorizacion", u.inmueble_id AS "inmuebleId",
                   i.direccion_inmueble AS "direccionInmueble"
            FROM usuarios u
            LEFT JOIN inmuebles i ON i.id = u.inmueble_id
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

    @Query(value = SELECT_INVITADO + """
            JOIN residente_invitados ri ON ri.invitado_id = u.id
            WHERE ri.residente_id = :residenteId
            ORDER BY u.nombre_completo, u.id
            """ + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<InvitadoView> listarDeResidente(@Param("residenteId") Long residenteId,
                                         @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM residente_invitados WHERE residente_id = :residenteId", nativeQuery = true)
    long contarDeResidente(@Param("residenteId") Long residenteId);

    @Query(value = SELECT_INVITADO + "WHERE u.tipo_usuario = 'USUARIO_AUTORIZADO' AND u.identificacion = :identificacion",
            nativeQuery = true)
    Optional<InvitadoView> buscarPorIdentificacion(@Param("identificacion") Long identificacion);

    @Query(value = SELECT_INVITADO + "WHERE u.tipo_usuario = 'USUARIO_AUTORIZADO' AND u.id = :id", nativeQuery = true)
    Optional<InvitadoView> buscarPorId(@Param("id") Long id);

    String SELECT_ANFITRION = """
            SELECT r.id AS "id", r.identificacion AS "identificacion", r.nombre_completo AS "nombreCompleto",
                   r.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble"
            FROM residente_invitados ri
            JOIN usuarios r ON r.id = ri.residente_id
            LEFT JOIN inmuebles i ON i.id = r.inmueble_id
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
}
