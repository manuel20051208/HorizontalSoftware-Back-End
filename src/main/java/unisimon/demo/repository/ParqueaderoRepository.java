package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Paqueadero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Paqueadero de un inmueble (a lo sumo uno). SQL nativo. */
public interface ParqueaderoRepository extends Repository<Paqueadero, Long> {

    interface ParqueaderoView {
        Long getId();

        Long getInmuebleId();

        BigDecimal getValorMensual();
    }

    @Query(value = "INSERT INTO parqueaderos (inmueble_id, valor_mensual) VALUES (:inmuebleId, :valor) RETURNING id",
            nativeQuery = true)
    Long insertar(@Param("inmuebleId") Long inmuebleId, @Param("valor") BigDecimal valor);

    @Query(value = """
            SELECT p.id AS "id", p.inmueble_id AS "inmuebleId", p.valor_mensual AS "valorMensual"
            FROM parqueaderos p WHERE p.inmueble_id = :inmuebleId
            """, nativeQuery = true)
    Optional<ParqueaderoView> buscarPorInmueble(@Param("inmuebleId") Long inmuebleId);

    @Query(value = """
            SELECT p.id AS "id", p.inmueble_id AS "inmuebleId", p.valor_mensual AS "valorMensual"
            FROM parqueaderos p WHERE p.id = :id
            """, nativeQuery = true)
    Optional<ParqueaderoView> buscarPorId(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE parqueaderos SET valor_mensual = :valor WHERE inmueble_id = :inmuebleId", nativeQuery = true)
    int actualizarValorMensual(@Param("inmuebleId") Long inmuebleId, @Param("valor") BigDecimal valor);

    String SELECT_PARQUEADERO = """
            SELECT p.id AS "id", p.inmueble_id AS "inmuebleId", p.valor_mensual AS "valorMensual"
            FROM parqueaderos p
            """;

    @Query(value = SELECT_PARQUEADERO + "ORDER BY p.inmueble_id, p.id" + SqlPaginacion.LIMITE_DESPLAZAMIENTO,
            nativeQuery = true)
    List<ParqueaderoView> listar(@Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM parqueaderos", nativeQuery = true)
    long contar();

    @Modifying
    @Query(value = "UPDATE parqueaderos SET valor_mensual = :valor WHERE id = :id", nativeQuery = true)
    int actualizarValorMensualPorId(@Param("id") Long id, @Param("valor") BigDecimal valor);

    @Modifying
    @Query(value = "DELETE FROM parqueaderos WHERE id = :id", nativeQuery = true)
    int eliminar(@Param("id") Long id);
}
