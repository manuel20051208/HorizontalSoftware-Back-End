package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Pago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Pagos de administracion. SQL nativo. */
public interface PagoRepository extends Repository<Pago, Long> {

    interface PagoView {
        Long getId();

        Long getInmuebleId();

        String getDireccionInmueble();

        Long getPasarelaId();

        BigDecimal getMonto();

        LocalDate getFechaPago();

        String getConcepto();

        String getMetodoPago();

        String getEstado();

        String getReferencia();
    }

    String SELECT_PAGO = """
            SELECT p.id AS "id", p.inmueble_id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble",
                   p.pasarela_id AS "pasarelaId", p.monto AS "monto", p.fecha_pago AS "fechaPago",
                   p.concepto AS "concepto", p.metodo_pago AS "metodoPago", p.estado AS "estado",
                   p.referencia AS "referencia"
            FROM pagos p
            JOIN inmuebles i ON i.id = p.inmueble_id
            """;

    @Query(value = """
            INSERT INTO pagos (inmueble_id, pasarela_id, monto, fecha_pago, concepto, metodo_pago, estado, referencia)
            VALUES (:#{#p.inmueble.id}, :#{#p.pasarela?.id}, :#{#p.monto}, :#{#p.fechaPago}, :#{#p.concepto},
                    :#{#p.metodoPago}, :#{#p.estado}, :#{#p.referencia})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("p") Pago p);

    @Query(value = SELECT_PAGO + "WHERE p.id = :id", nativeQuery = true)
    Optional<PagoView> buscarPorId(@Param("id") Long id);

    @Query(value = SELECT_PAGO + "WHERE p.referencia = :referencia", nativeQuery = true)
    Optional<PagoView> buscarPorReferencia(@Param("referencia") String referencia);

    @Query(value = SELECT_PAGO + "WHERE p.inmueble_id = :inmuebleId ORDER BY p.id DESC"
            + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<PagoView> listarPorInmueble(@Param("inmuebleId") Long inmuebleId,
                                     @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM pagos WHERE inmueble_id = :inmuebleId", nativeQuery = true)
    long contarPorInmueble(@Param("inmuebleId") Long inmuebleId);

    String FILTRO_LISTADO = """
            WHERE (CAST(:inmuebleId AS BIGINT) IS NULL OR p.inmueble_id = CAST(:inmuebleId AS BIGINT))
              AND (CAST(:estado AS VARCHAR) IS NULL OR p.estado = CAST(:estado AS VARCHAR))
            """;

    @Query(value = SELECT_PAGO + FILTRO_LISTADO + "ORDER BY p.id DESC" + SqlPaginacion.LIMITE_DESPLAZAMIENTO,
            nativeQuery = true)
    List<PagoView> listar(@Param("inmuebleId") Long inmuebleId, @Param("estado") String estado,
                          @Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM pagos p " + FILTRO_LISTADO, nativeQuery = true)
    long contar(@Param("inmuebleId") Long inmuebleId, @Param("estado") String estado);

    @Query(value = """
            SELECT COALESCE(SUM(monto), 0) FROM pagos
            WHERE estado = :estado AND fecha_pago BETWEEN :desde AND :hasta
            """, nativeQuery = true)
    BigDecimal sumarPorEstadoEntre(@Param("estado") String estado, @Param("desde") LocalDate desde,
                                   @Param("hasta") LocalDate hasta);

    // ------------------------------------------------------------------ cambios por columna

    @Modifying
    @Query(value = "UPDATE pagos SET estado = :estado, fecha_pago = :fechaPago WHERE id = :id", nativeQuery = true)
    int actualizarEstado(@Param("id") Long id, @Param("estado") String estado, @Param("fechaPago") LocalDate fechaPago);

    @Modifying
    @Query(value = "UPDATE pagos SET referencia = :referencia, pasarela_id = :pasarelaId WHERE id = :id",
            nativeQuery = true)
    int actualizarTransaccion(@Param("id") Long id, @Param("referencia") String referencia,
                              @Param("pasarelaId") Long pasarelaId);
}
