package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Liquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Liquidaciones mensuales. SQL nativo; las modificaciones tocan solo las columnas necesarias. */
public interface LiquidacionRepository extends Repository<Liquidacion, Long> {

    interface LiquidacionView {
        Long getId();

        Long getInmuebleId();

        String getDireccionInmueble();

        LocalDate getPeriodo();

        LocalDate getFechaGeneracion();

        LocalDate getFechaLimite();

        BigDecimal getCuotaAdministracion();

        BigDecimal getValorServiciosComunes();

        BigDecimal getValorMultas();

        BigDecimal getTotal();

        String getEstado();
    }

    String SELECT_LIQUIDACION = """
            SELECT l.id AS "id", l.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble",
                   l.periodo AS "periodo", l.fecha_generacion AS "fechaGeneracion", l.fecha_limite AS "fechaLimite",
                   l.cuota_administracion AS "cuotaAdministracion",
                   l.valor_servicios_comunes AS "valorServiciosComunes", l.valor_multas AS "valorMultas",
                   l.total AS "total", l.estado AS "estado"
            FROM liquidaciones l
            JOIN inmuebles i ON i.id = l.inmueble_id
            """;

    @Query(value = """
            INSERT INTO liquidaciones (inmueble_id, periodo, fecha_generacion, fecha_limite, cuota_administracion,
                                       valor_servicios_comunes, valor_multas, total, estado)
            VALUES (:#{#l.inmueble.id}, :#{#l.periodo}, :#{#l.fechaGeneracion}, :#{#l.fechaLimite},
                    :#{#l.cuotaAdministracion}, :#{#l.valorServiciosComunes}, :#{#l.valorMultas}, :#{#l.total},
                    :#{#l.estado})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("l") Liquidacion l);

    @Query(value = SELECT_LIQUIDACION + "WHERE l.id = :id", nativeQuery = true)
    Optional<LiquidacionView> buscarPorId(@Param("id") Long id);

    /** La liquidacion vigente de un inmueble (la que apunta inmuebles.liquidacion_actual_id). */
    @Query(value = SELECT_LIQUIDACION + "WHERE l.id = (SELECT liquidacion_actual_id FROM inmuebles WHERE id = :inmuebleId)",
            nativeQuery = true)
    Optional<LiquidacionView> buscarVigenteDeInmueble(@Param("inmuebleId") Long inmuebleId);

    /** Liquidaciones vigentes de los inmuebles activos: base para la generacion masiva del mes siguiente. */
    @Query(value = SELECT_LIQUIDACION + "WHERE i.is_active AND l.id = i.liquidacion_actual_id ORDER BY i.id",
            nativeQuery = true)
    List<LiquidacionView> listarVigentesDeInmueblesActivos();

    @Query(value = SELECT_LIQUIDACION + "WHERE l.estado = :estado ORDER BY l.id", nativeQuery = true)
    List<LiquidacionView> listarPorEstado(@Param("estado") String estado);

    @Query(value = SELECT_LIQUIDACION + """
            WHERE (CAST(:inmuebleId AS BIGINT) IS NULL OR l.inmueble_id = CAST(:inmuebleId AS BIGINT))
              AND (CAST(:estado AS VARCHAR) IS NULL OR l.estado = CAST(:estado AS VARCHAR))
              AND (CAST(:periodo AS DATE) IS NULL OR l.periodo = CAST(:periodo AS DATE))
            ORDER BY l.periodo DESC, i.direccion_inmueble
            """, nativeQuery = true)
    List<LiquidacionView> listar(@Param("inmuebleId") Long inmuebleId, @Param("estado") String estado,
                                 @Param("periodo") LocalDate periodo);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM liquidaciones WHERE inmueble_id = :inmuebleId AND periodo = :periodo)",
            nativeQuery = true)
    boolean existePorInmuebleYPeriodo(@Param("inmuebleId") Long inmuebleId, @Param("periodo") LocalDate periodo);

    /** Suma de todo lo que el inmueble debe: sus liquidaciones que no estan al dia. */
    @Query(value = """
            SELECT COALESCE(SUM(total), 0) FROM liquidaciones
            WHERE inmueble_id = :inmuebleId AND estado <> :alDia
            """, nativeQuery = true)
    BigDecimal saldoPendiente(@Param("inmuebleId") Long inmuebleId, @Param("alDia") String alDia);

    default BigDecimal saldoPendiente(Long inmuebleId) {
        return saldoPendiente(inmuebleId, Liquidacion.AL_DIA);
    }

    // ------------------------------------------------------------------ cambios por columna

    @Modifying
    @Query(value = "UPDATE liquidaciones SET estado = :estado WHERE id = :id", nativeQuery = true)
    int actualizarEstado(@Param("id") Long id, @Param("estado") String estado);

    @Modifying
    @Query(value = "UPDATE liquidaciones SET estado = :estado WHERE id IN (:ids)", nativeQuery = true)
    int actualizarEstadoPorIds(@Param("ids") List<Long> ids, @Param("estado") String estado);

    @Modifying
    @Query(value = """
            UPDATE liquidaciones
            SET cuota_administracion = :cuota, valor_servicios_comunes = :servicios, valor_multas = :multas,
                total = :total
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarValores(@Param("id") Long id, @Param("cuota") BigDecimal cuota,
                          @Param("servicios") BigDecimal servicios, @Param("multas") BigDecimal multas,
                          @Param("total") BigDecimal total);

    /** Deja al dia todas las liquidaciones pendientes o en mora del inmueble (cuando entra un pago aprobado). */
    @Modifying
    @Query(value = "UPDATE liquidaciones SET estado = :alDia WHERE inmueble_id = :inmuebleId AND estado <> :alDia",
            nativeQuery = true)
    int marcarPagadasDeInmueble(@Param("inmuebleId") Long inmuebleId, @Param("alDia") String alDia);

    default int marcarPagadasDeInmueble(Long inmuebleId) {
        return marcarPagadasDeInmueble(inmuebleId, Liquidacion.AL_DIA);
    }
}
