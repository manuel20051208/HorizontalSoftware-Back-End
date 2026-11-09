package unisimon.demo.repository;

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
                   a.parqueadero_id AS "parqueaderoId", u.inmueble_id AS "inmuebleId",
                   i.direccion_inmueble AS "direccionInmueble"
            FROM autos a
            LEFT JOIN usuarios u ON u.id = a.dueno_id
            LEFT JOIN inmuebles i ON i.id = u.inmueble_id
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

    /** consultarDuenos(inmueble, id): quien es el dueno del auto dentro de ese inmueble. */
    @Query(value = """
            SELECT u.id AS "id", u.identificacion AS "identificacion", u.nombre_completo AS "nombreCompleto",
                   u.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble"
            FROM autos a
            JOIN usuarios u ON u.id = a.dueno_id
            JOIN inmuebles i ON i.id = u.inmueble_id
            WHERE a.id = :autoId AND u.inmueble_id = :inmuebleId
            """, nativeQuery = true)
    List<DuenoView> listarDuenos(@Param("inmuebleId") Long inmuebleId, @Param("autoId") Long autoId);
}
