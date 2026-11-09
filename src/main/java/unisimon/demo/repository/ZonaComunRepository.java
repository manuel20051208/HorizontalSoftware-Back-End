package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.ZonaComun;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Zonas comunes. SQL nativo. */
public interface ZonaComunRepository extends Repository<ZonaComun, Long> {

    interface ZonaView {
        Long getId();

        String getNombreZonaComun();

        Integer getCapacidad();

        LocalTime getHoraInicio();

        LocalTime getHoraFin();

        Boolean getIsActive();

        /** Reservas registradas para la zona. */
        Long getReservasActuales();
    }

    String SELECT_ZONA = """
            SELECT z.id AS "id", z.nombre_zona_comun AS "nombreZonaComun", z.capacidad AS "capacidad",
                   z.hora_inicio AS "horaInicio", z.hora_fin AS "horaFin", z.is_active AS "isActive",
                   (SELECT COUNT(*) FROM reservas_zona_comun r WHERE r.zona_comun_id = z.id) AS "reservasActuales"
            FROM zonas_comunes z
            """;

    @Query(value = """
            INSERT INTO zonas_comunes (nombre_zona_comun, capacidad, hora_inicio, hora_fin, is_active)
            VALUES (:#{#z.nombreZonaComun}, :#{#z.capacidad}, :#{#z.horaInicio}, :#{#z.horaFin}, :#{#z.isActive})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("z") ZonaComun z);

    @Query(value = SELECT_ZONA + "WHERE z.id = :id", nativeQuery = true)
    Optional<ZonaView> buscarPorId(@Param("id") Long id);

    @Query(value = SELECT_ZONA + "ORDER BY z.nombre_zona_comun", nativeQuery = true)
    List<ZonaView> listar();

    /** mostrarZonasComunes: las zonas activas asignadas a un inmueble. */
    @Query(value = SELECT_ZONA + """
            JOIN inmueble_zonas_comunes iz ON iz.zona_comun_id = z.id
            WHERE iz.inmueble_id = :inmuebleId AND z.is_active
            ORDER BY z.nombre_zona_comun
            """, nativeQuery = true)
    List<ZonaView> listarActivasDeInmueble(@Param("inmuebleId") Long inmuebleId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM zonas_comunes WHERE LOWER(nombre_zona_comun) = LOWER(:nombre))",
            nativeQuery = true)
    boolean existePorNombre(@Param("nombre") String nombre);

    @Query(value = "SELECT COUNT(*) FROM zonas_comunes WHERE id IN (:ids)", nativeQuery = true)
    long contarPorIds(@Param("ids") List<Long> ids);

    @Modifying
    @Query(value = "UPDATE zonas_comunes SET is_active = :activa WHERE id = :id", nativeQuery = true)
    int actualizarEstado(@Param("id") Long id, @Param("activa") boolean activa);
}
