package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.ReservaZonaComun;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Reservas de zonas comunes. SQL nativo.
 * <p>
 * Una reserva ocupa el intervalo [fecha_hora_inicio, fecha_hora_fin). Dos reservas se solapan cuando cada una
 * empieza antes de que termine la otra. La hora actual ({@code :ahora}) la pasa el servicio desde el reloj de la
 * aplicacion (hora de Colombia): el SQL no usa {@code NOW()}, que depende de la zona horaria de la sesion.
 */
public interface ReservaZonaComunRepository extends Repository<ReservaZonaComun, Long> {

    interface ReservaView {
        Long getId();

        Long getZonaComunId();

        String getNombreZonaComun();

        LocalTime getHoraInicio();

        LocalTime getHoraFin();

        Boolean getZonaActiva();

        LocalDateTime getFechaHoraInicio();

        LocalDateTime getFechaHoraFin();

        Long getDuenoReservaId();

        String getNombreDueno();

        Long getInmuebleId();

        String getDireccionInmueble();
    }

    /** Reserva en curso que le da derecho a una persona (o a su anfitrion) a entrar a una zona. */
    interface AccesoView {
        Long getZonaComunId();

        String getNombreZonaComun();

        LocalTime getHoraInicio();

        LocalTime getHoraFin();

        Boolean getZonaActiva();

        LocalDateTime getReservaHasta();
    }

    String SELECT_RESERVA = """
            SELECT r.id AS "id", r.zona_comun_id AS "zonaComunId", z.nombre_zona_comun AS "nombreZonaComun",
                   z.hora_inicio AS "horaInicio", z.hora_fin AS "horaFin", z.is_active AS "zonaActiva",
                   r.fecha_hora_inicio AS "fechaHoraInicio", r.fecha_hora_fin AS "fechaHoraFin",
                   r.dueno_reserva_id AS "duenoReservaId", u.nombre_completo AS "nombreDueno",
                   u.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble"
            FROM reservas_zona_comun r
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            JOIN usuarios u ON u.id = r.dueno_reserva_id
            LEFT JOIN inmuebles i ON i.id = u.inmueble_id
            """;

    /**
     * Filtro opcional por estado calculado. Los nombres de estado llegan como parametros desde las constantes de
     * {@link ReservaZonaComun} (ver los metodos default): el SQL no los repite.
     */
    String FILTRO_ESTADO = """
            (CAST(:estado AS VARCHAR) IS NULL
             OR (CAST(:estado AS VARCHAR) = CAST(:programada AS VARCHAR)
                 AND r.fecha_hora_inicio > CAST(:ahora AS TIMESTAMP))
             OR (CAST(:estado AS VARCHAR) = CAST(:enCurso AS VARCHAR)
                 AND r.fecha_hora_inicio <= CAST(:ahora AS TIMESTAMP) AND r.fecha_hora_fin > CAST(:ahora AS TIMESTAMP))
             OR (CAST(:estado AS VARCHAR) = CAST(:finalizada AS VARCHAR)
                 AND r.fecha_hora_fin <= CAST(:ahora AS TIMESTAMP)))
            """;

    @Query(value = """
            INSERT INTO reservas_zona_comun (dueno_reserva_id, zona_comun_id, fecha_hora_inicio, fecha_hora_fin)
            VALUES (:#{#r.duenoReserva.id}, :#{#r.zonaComun.id}, :#{#r.fechaHoraInicio}, :#{#r.fechaHoraFin})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("r") ReservaZonaComun r);

    @Query(value = SELECT_RESERVA + "WHERE r.id = :id", nativeQuery = true)
    Optional<ReservaView> buscarPorId(@Param("id") Long id);

    // ------------------------------------------------------------------ listados paginados

    @Query(value = SELECT_RESERVA + "WHERE r.dueno_reserva_id = :residenteId AND " + FILTRO_ESTADO
            + "ORDER BY r.fecha_hora_inicio DESC, r.id DESC" + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<ReservaView> listarPorResidente(@Param("residenteId") Long residenteId, @Param("estado") String estado,
                                         @Param("ahora") LocalDateTime ahora,
                                         @Param("programada") String programada, @Param("enCurso") String enCurso,
                                         @Param("finalizada") String finalizada,
                                         @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM reservas_zona_comun r WHERE r.dueno_reserva_id = :residenteId AND "
            + FILTRO_ESTADO, nativeQuery = true)
    long contarPorResidente(@Param("residenteId") Long residenteId, @Param("estado") String estado,
                            @Param("ahora") LocalDateTime ahora,
                            @Param("programada") String programada, @Param("enCurso") String enCurso,
                            @Param("finalizada") String finalizada);

    /** Las reservas del residente (mas recientes primero), opcionalmente solo las de un estado. */
    default List<ReservaView> listarPorResidente(Long residenteId, String estado, LocalDateTime ahora,
                                                 int limite, long desplazamiento) {
        return listarPorResidente(residenteId, estado, ahora, ReservaZonaComun.PROGRAMADA, ReservaZonaComun.EN_CURSO,
                ReservaZonaComun.FINALIZADA, limite, desplazamiento);
    }

    default long contarPorResidente(Long residenteId, String estado, LocalDateTime ahora) {
        return contarPorResidente(residenteId, estado, ahora, ReservaZonaComun.PROGRAMADA, ReservaZonaComun.EN_CURSO,
                ReservaZonaComun.FINALIZADA);
    }

    @Query(value = SELECT_RESERVA + "WHERE " + FILTRO_ESTADO + "ORDER BY r.fecha_hora_inicio, r.id"
            + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<ReservaView> listarTodas(@Param("estado") String estado, @Param("ahora") LocalDateTime ahora,
                                  @Param("programada") String programada, @Param("enCurso") String enCurso,
                                  @Param("finalizada") String finalizada,
                                  @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM reservas_zona_comun r WHERE " + FILTRO_ESTADO, nativeQuery = true)
    long contarTodas(@Param("estado") String estado, @Param("ahora") LocalDateTime ahora,
                     @Param("programada") String programada, @Param("enCurso") String enCurso,
                     @Param("finalizada") String finalizada);

    /** Todas las reservas (por hora de inicio), opcionalmente solo las de un estado. */
    default List<ReservaView> listarTodas(String estado, LocalDateTime ahora, int limite, long desplazamiento) {
        return listarTodas(estado, ahora, ReservaZonaComun.PROGRAMADA, ReservaZonaComun.EN_CURSO,
                ReservaZonaComun.FINALIZADA, limite, desplazamiento);
    }

    default long contarTodas(String estado, LocalDateTime ahora) {
        return contarTodas(estado, ahora, ReservaZonaComun.PROGRAMADA, ReservaZonaComun.EN_CURSO,
                ReservaZonaComun.FINALIZADA);
    }

    // ------------------------------------------------------------------ cupo y solapes

    /** Reservas de la zona que se cruzan con [inicio, fin): se comparan con la capacidad. */
    @Query(value = """
            SELECT COUNT(*) FROM reservas_zona_comun
            WHERE zona_comun_id = :zonaId AND fecha_hora_inicio < :fin AND fecha_hora_fin > :inicio
            """, nativeQuery = true)
    long contarSolapadas(@Param("zonaId") Long zonaId, @Param("inicio") LocalDateTime inicio,
                         @Param("fin") LocalDateTime fin);

    /** El residente ya tiene una reserva de esa zona que se cruza con [inicio, fin). */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM reservas_zona_comun
                           WHERE dueno_reserva_id = :residenteId AND zona_comun_id = :zonaId
                             AND fecha_hora_inicio < :fin AND fecha_hora_fin > :inicio)
            """, nativeQuery = true)
    boolean existeSolapadaDeResidente(@Param("residenteId") Long residenteId, @Param("zonaId") Long zonaId,
                                      @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // ------------------------------------------------------------------ acceso del vigilante

    /** Zonas con una reserva EN CURSO de la persona o de un residente que la autorizo como invitado. */
    @Query(value = """
            SELECT z.id AS "zonaComunId", z.nombre_zona_comun AS "nombreZonaComun", z.hora_inicio AS "horaInicio",
                   z.hora_fin AS "horaFin", z.is_active AS "zonaActiva", r.fecha_hora_fin AS "reservaHasta"
            FROM reservas_zona_comun r
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            JOIN usuarios u ON u.id = r.dueno_reserva_id
            WHERE u.identificacion = :identificacion AND u.active
              AND r.fecha_hora_inicio <= :ahora AND r.fecha_hora_fin > :ahora
            UNION
            SELECT z.id, z.nombre_zona_comun, z.hora_inicio, z.hora_fin, z.is_active, r.fecha_hora_fin
            FROM usuarios invitado
            JOIN residente_invitados ri ON ri.invitado_id = invitado.id
            JOIN reservas_zona_comun r ON r.dueno_reserva_id = ri.residente_id
            JOIN zonas_comunes z ON z.id = r.zona_comun_id
            WHERE invitado.identificacion = :identificacion AND invitado.active
              AND r.fecha_hora_inicio <= :ahora AND r.fecha_hora_fin > :ahora
            """, nativeQuery = true)
    List<AccesoView> listarZonasConAcceso(@Param("identificacion") Long identificacion,
                                          @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query(value = "DELETE FROM reservas_zona_comun WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
