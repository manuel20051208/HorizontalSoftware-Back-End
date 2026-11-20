package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Auto;

import java.util.List;
import java.util.Optional;

/** Autos de los residentes. SQL nativo. */
public interface AutoRepository extends Repository<Auto, Long> {

    interface AutoView {
        Long getId();

        Long getDuenoId();

        String getNombreDueno();

        Long getPlaca();

        Long getParqueaderoId();

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    interface DuenoView {
        Long getId();

        Long getIdentificacion();

        String getNombreCompleto();

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    String SELECT_AUTO = """
            SELECT a.id AS "id", a.dueno_id AS "duenoId", u.nombre_completo AS "nombreDueno", a.placa AS "placa",
                   a.parqueadero_id AS "parqueaderoId", i.id AS "inmuebleId",
                   i.direccion_inmueble AS "direccionInmueble"
            FROM autos a
            LEFT JOIN usuarios u ON u.id = a.dueno_id
            LEFT JOIN LATERAL (
                SELECT i.id, i.direccion_inmueble
                FROM usuario_inmuebles ui
                JOIN inmuebles i ON i.id = ui.inmueble_id
                WHERE ui.usuario_id = a.dueno_id
                ORDER BY i.direccion_inmueble
                LIMIT 1
            ) i ON TRUE
            """;

    @Query(value = """
            INSERT INTO autos (dueno_id, placa, parqueadero_id)
            VALUES (:#{#a.dueno.id}, :#{#a.placa}, :#{#a.parqueadero?.id})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("a") Auto a);

    @Query(value = SELECT_AUTO + "WHERE a.dueno_id = :duenoId", nativeQuery = true)
    Optional<AutoView> buscarPorDueno(@Param("duenoId") Long duenoId);

    @Query(value = SELECT_AUTO + "WHERE a.id = :id", nativeQuery = true)
    Optional<AutoView> buscarPorId(@Param("id") Long id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM autos WHERE placa = :placa)", nativeQuery = true)
    boolean existePorPlaca(@Param("placa") Long placa);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM autos WHERE parqueadero_id = :parqueaderoId)", nativeQuery = true)
    boolean existePorParqueadero(@Param("parqueaderoId") Long parqueaderoId);

    /** El auto del residente deja libre el parqueadero de ese inmueble; el auto sigue siendo suyo. */
    @Modifying
    @Query(value = """
            UPDATE autos SET parqueadero_id = NULL
            WHERE dueno_id = :duenoId
              AND parqueadero_id IN (SELECT p.id FROM parqueaderos p WHERE p.inmueble_id = :inmuebleId)
            """, nativeQuery = true)
    int liberarParqueaderoDeInmueble(@Param("duenoId") Long duenoId, @Param("inmuebleId") Long inmuebleId);

    /** consultarDuenos(inmueble, id): quien es el dueno del auto dentro de ese inmueble. */
    @Query(value = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   i.id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble"
            FROM autos a
            JOIN usuarios u ON u.id = a.dueno_id
            JOIN usuario_inmuebles ui ON ui.usuario_id = u.id AND ui.inmueble_id = :inmuebleId
            JOIN inmuebles i ON i.id = ui.inmueble_id
            WHERE a.id = :autoId
            """, nativeQuery = true)
    List<DuenoView> listarDuenos(@Param("inmuebleId") Long inmuebleId, @Param("autoId") Long autoId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM autos WHERE placa = :placa AND id <> :id)", nativeQuery = true)
    boolean existePorPlacaEnOtro(@Param("placa") Long placa, @Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE autos SET placa = :placa, parqueadero_id = :parqueaderoId WHERE id = :id",
            nativeQuery = true)
    int actualizarDatos(@Param("id") Long id, @Param("placa") Long placa, @Param("parqueaderoId") Long parqueaderoId);

    /** Auto -> Paqueadero es asociacion: al borrar el parqueadero el auto queda sin parqueadero, no se borra. */
    @Modifying
    @Query(value = "UPDATE autos SET parqueadero_id = NULL WHERE parqueadero_id = :parqueaderoId", nativeQuery = true)
    int liberarParqueadero(@Param("parqueaderoId") Long parqueaderoId);

    /** Libera el parqueadero del inmueble, sea de quien sea el auto (antes de borrar el inmueble). */
    @Modifying
    @Query(value = """
            UPDATE autos SET parqueadero_id = NULL
            WHERE parqueadero_id IN (SELECT p.id FROM parqueaderos p WHERE p.inmueble_id = :inmuebleId)
            """, nativeQuery = true)
    int liberarParqueaderosDeInmueble(@Param("inmuebleId") Long inmuebleId);

    @Modifying
    @Query(value = "DELETE FROM autos WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
