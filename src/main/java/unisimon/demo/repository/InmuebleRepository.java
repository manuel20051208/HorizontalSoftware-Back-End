package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.Liquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Inmuebles, su estado de cuenta agregado y las zonas comunes que tienen asignadas. SQL nativo. */
public interface InmuebleRepository extends Repository<Inmueble, Long> {

    interface InmuebleBasicoView {
        Long getId();

        String getDireccionInmueble();

        Boolean getIsActive();

        Long getLiquidacionActualId();
    }

    /** Inmueble + resumen de lo que debe (todas sus liquidaciones no pagadas). */
    interface InmuebleResumenView {
        Long getId();

        String getDireccionInmueble();

        Boolean getIsActive();

        BigDecimal getSaldoPendiente();

        LocalDate getFechaLimitePendiente();

        Boolean getTieneMora();

        Long getTotalLiquidaciones();

        BigDecimal getValorParqueadero();

        Long getLiquidacionActualId();
    }

    String SELECT_RESUMEN = """
            SELECT i.id AS "id", i.direccion_inmueble AS "direccionInmueble", i.is_active AS "isActive",
                   COALESCE(SUM(l.total) FILTER (WHERE l.estado <> :alDia), 0) AS "saldoPendiente",
                   MIN(l.fecha_limite) FILTER (WHERE l.estado <> :alDia) AS "fechaLimitePendiente",
                   COALESCE(BOOL_OR(l.estado = :enMora), FALSE) AS "tieneMora",
                   COUNT(l.id) AS "totalLiquidaciones",
                   COALESCE(p.valor_mensual, 0) AS "valorParqueadero",
                   i.liquidacion_actual_id AS "liquidacionActualId"
            FROM inmuebles i
            LEFT JOIN liquidaciones l ON l.inmueble_id = i.id
            LEFT JOIN parqueaderos p ON p.inmueble_id = i.id
            """;

    String GRUPO_RESUMEN = " GROUP BY i.id, p.valor_mensual ";

    @Query(value = "INSERT INTO inmuebles (direccion_inmueble) VALUES (:direccion) RETURNING id", nativeQuery = true)
    Long insertar(@Param("direccion") String direccion);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM inmuebles WHERE id = :id)", nativeQuery = true)
    boolean existePorId(@Param("id") Long id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM inmuebles WHERE LOWER(direccion_inmueble) = LOWER(:direccion))",
            nativeQuery = true)
    boolean existePorDireccion(@Param("direccion") String direccion);

    @Query(value = """
            SELECT i.id AS "id", i.direccion_inmueble AS "direccionInmueble", i.is_active AS "isActive",
                   i.liquidacion_actual_id AS "liquidacionActualId"
            FROM inmuebles i WHERE i.id = :id
            """, nativeQuery = true)
    Optional<InmuebleBasicoView> buscarBasico(@Param("id") Long id);

    @Query(value = SELECT_RESUMEN + GRUPO_RESUMEN + "ORDER BY i.direccion_inmueble", nativeQuery = true)
    List<InmuebleResumenView> listarResumen(@Param("alDia") String alDia, @Param("enMora") String enMora);

    @Query(value = SELECT_RESUMEN + "WHERE i.id = :id" + GRUPO_RESUMEN, nativeQuery = true)
    Optional<InmuebleResumenView> buscarResumen(@Param("id") Long id, @Param("alDia") String alDia,
                                                @Param("enMora") String enMora);

    /** Los estados salen de las constantes del dominio: el SQL no los repite. */
    default List<InmuebleResumenView> listarResumen() {
        return listarResumen(Liquidacion.AL_DIA, Liquidacion.EN_MORA);
    }

    default Optional<InmuebleResumenView> buscarResumen(Long id) {
        return buscarResumen(id, Liquidacion.AL_DIA, Liquidacion.EN_MORA);
    }

    @Query(value = SELECT_RESUMEN + GRUPO_RESUMEN + "ORDER BY i.direccion_inmueble, i.id"
            + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<InmuebleResumenView> listarResumenPagina(@Param("alDia") String alDia, @Param("enMora") String enMora,
                                                  @Param("limite") int limite,
                                                  @Param("desplazamiento") long desplazamiento);

    /** Una pagina del listado de inmuebles (mismo orden que {@link #listarResumen()}). */
    default List<InmuebleResumenView> listarResumen(int limite, long desplazamiento) {
        return listarResumenPagina(Liquidacion.AL_DIA, Liquidacion.EN_MORA, limite, desplazamiento);
    }

    @Query(value = "SELECT COUNT(*) FROM inmuebles", nativeQuery = true)
    long contar();

    @Query(value = "SELECT id FROM inmuebles WHERE is_active ORDER BY id", nativeQuery = true)
    List<Long> listarIdsActivos();

    @Modifying
    @Query(value = "UPDATE inmuebles SET liquidacion_actual_id = :liquidacionId WHERE id = :id", nativeQuery = true)
    int actualizarLiquidacionActual(@Param("id") Long id, @Param("liquidacionId") Long liquidacionId);

    // ------------------------------------------------------------------ zonas comunes del inmueble

    @Modifying
    @Query(value = "DELETE FROM inmueble_zonas_comunes WHERE inmueble_id = :inmuebleId", nativeQuery = true)
    int limpiarZonas(@Param("inmuebleId") Long inmuebleId);

    @Modifying
    @Query(value = """
            INSERT INTO inmueble_zonas_comunes (inmueble_id, zona_comun_id)
            SELECT :inmuebleId, z.id FROM zonas_comunes z WHERE z.id IN (:zonaIds)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int asignarZonas(@Param("inmuebleId") Long inmuebleId, @Param("zonaIds") List<Long> zonaIds);

    @Modifying
    @Query(value = """
            INSERT INTO inmueble_zonas_comunes (inmueble_id, zona_comun_id)
            SELECT :inmuebleId, z.id FROM zonas_comunes z WHERE z.is_active
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int asignarZonasActivas(@Param("inmuebleId") Long inmuebleId);

    @Modifying
    @Query(value = """
            INSERT INTO inmueble_zonas_comunes (inmueble_id, zona_comun_id)
            SELECT i.id, :zonaId FROM inmuebles i
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int asignarZonaATodos(@Param("zonaId") Long zonaId);
}
