package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Parqueadero;

import java.math.BigDecimal;
import java.util.Optional;

/** Parqueadero de un inmueble (a lo sumo uno). SQL nativo. */
public interface ParqueaderoRepository extends Repository<Parqueadero, Long> {

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
}
