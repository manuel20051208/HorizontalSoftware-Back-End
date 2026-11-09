package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Rompimiento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Asignaciones de reglas a zonas comunes. SQL nativo. */
public interface RompimientoRepository extends Repository<Rompimiento, Long> {

    interface RompimientoView {
        Long getId();

        Long getZonaComunId();

        String getNombreZonaComun();

        Long getReglaReservaId();

        LocalDate getFechaAplicacion();

        Boolean getActiva();

        String getObservaciones();
    }

    String SELECT_ROMPIMIENTO = """
            SELECT r.id AS "id", r.zona_comun_id AS "zonaComunId", z.nombre_zona_comun AS "nombreZonaComun",
                   r.regla_reserva_id AS "reglaReservaId", r.fecha_aplicacion AS "fechaAplicacion",
                   r.activa AS "activa", r.observaciones AS "observaciones"
            FROM rompimientos r
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            """;

    @Query(value = """
            INSERT INTO rompimientos (zona_comun_id, regla_reserva_id, fecha_aplicacion, activa, observaciones)
            VALUES (:#{#r.zonaComun.id}, :#{#r.reglaReserva.id}, :#{#r.fechaAplicacion}, :#{#r.activa},
                    :#{#r.observaciones})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("r") Rompimiento r);

    @Query(value = SELECT_ROMPIMIENTO + "WHERE r.id = :id", nativeQuery = true)
    Optional<RompimientoView> buscarPorId(@Param("id") Long id);

    @Query(value = SELECT_ROMPIMIENTO + "WHERE r.regla_reserva_id = :reglaId ORDER BY z.nombre_zona_comun",
            nativeQuery = true)
    List<RompimientoView> listarPorRegla(@Param("reglaId") Long reglaId);

    @Modifying
    @Query(value = "UPDATE rompimientos SET activa = :activa, fecha_aplicacion = :fechaAplicacion WHERE id = :id",
            nativeQuery = true)
    int actualizarActiva(@Param("id") Long id, @Param("activa") boolean activa,
                         @Param("fechaAplicacion") LocalDate fechaAplicacion);
}
