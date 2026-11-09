package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.ReservaZonaComun;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Reservas de zonas comunes. SQL nativo. */
public interface ReservaZonaComunRepository extends Repository<ReservaZonaComun, Long> {

    interface ReservaView {
        Long getId();

        Long getZonaComunId();

        String getNombreZonaComun();

        LocalTime getHoraInicio();

        LocalTime getHoraFin();

        Boolean getZonaActiva();

        Long getDuenoReservaId();

        String getNombreDueno();

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    /** Zonas a las que una persona tiene derecho a entrar porque ella (o su anfitrion) las reservo. */
    interface AccesoView {
        Long getZonaComunId();

        String getNombreZonaComun();

        LocalTime getHoraInicio();

        LocalTime getHoraFin();

        Boolean getZonaActiva();
    }

    String SELECT_RESERVA = """
            SELECT r.id AS "id", r.zona_comun_id AS "zonaComunId", z.nombre_zona_comun AS "nombreZonaComun",
                   z.hora_inicio AS "horaInicio", z.hora_fin AS "horaFin", z.is_active AS "zonaActiva",
                   r.dueno_reserva_id AS "duenoReservaId", u.nombre_completo AS "nombreDueno",
                   u.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble"
            FROM reservas_zona_comun r
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            JOIN usuarios u ON u.id = r.dueno_reserva_id
            LEFT JOIN inmuebles i ON i.id = u.inmueble_id
            """;

    @Query(value = """
            INSERT INTO reservas_zona_comun (dueno_reserva_id, zona_comun_id)
            VALUES (:#{#r.duenoReserva.id}, :#{#r.zonaComun.id})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("r") ReservaZonaComun r);

    @Query(value = SELECT_RESERVA + "WHERE r.id = :id", nativeQuery = true)
    Optional<ReservaView> buscarPorId(@Param("id") Long id);

    @Query(value = SELECT_RESERVA + "WHERE r.dueno_reserva_id = :residenteId ORDER BY r.id DESC", nativeQuery = true)
    List<ReservaView> listarPorResidente(@Param("residenteId") Long residenteId);

    @Query(value = SELECT_RESERVA + "ORDER BY z.hora_inicio, r.id", nativeQuery = true)
    List<ReservaView> listarTodas();

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM reservas_zona_comun
                           WHERE dueno_reserva_id = :residenteId AND zona_comun_id = :zonaId)
            """, nativeQuery = true)
    boolean existePorResidenteYZona(@Param("residenteId") Long residenteId, @Param("zonaId") Long zonaId);

    @Query(value = """
            SELECT z.id AS "zonaComunId", z.nombre_zona_comun AS "nombreZonaComun", z.hora_inicio AS "horaInicio",
                   z.hora_fin AS "horaFin", z.is_active AS "zonaActiva"
            FROM reservas_zona_comun r
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            JOIN usuarios u ON u.id = r.dueno_reserva_id
            WHERE u.identificacion = :identificacion AND u.active
            UNION
            SELECT z.id, z.nombre_zona_comun, z.hora_inicio, z.hora_fin, z.is_active
            FROM usuarios invitado
            JOIN residente_invitados ri ON ri.invitado_id = invitado.id
            JOIN reservas_zona_comun r ON r.dueno_reserva_id = ri.residente_id
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            WHERE invitado.identificacion = :identificacion AND invitado.active
            """, nativeQuery = true)
    List<AccesoView> listarZonasConAcceso(@Param("identificacion") Long identificacion);

    @Modifying
    @Query(value = "DELETE FROM reservas_zona_comun WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
