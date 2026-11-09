package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.PasarelaPago;

import java.util.Optional;

/** Configuracion de la pasarela de pagos. SQL nativo. */
public interface PasarelaPagoRepository extends Repository<PasarelaPago, Long> {

    interface PasarelaView {
        Long getId();

        String getNombrePasarela();

        String getUrlApi();

        String getApiKey();

        String getEstado();
    }

    String SELECT_PASARELA = """
            SELECT p.id AS "id", p.nombre_pasarela AS "nombrePasarela", p.url_api AS "urlApi",
                   p.api_key AS "apiKey", p.estado AS "estado"
            FROM pasarelas_pago p
            """;

    /** La pasarela configurada (el sistema usa una sola). */
    @Query(value = SELECT_PASARELA + "ORDER BY p.id LIMIT 1", nativeQuery = true)
    Optional<PasarelaView> buscarPrincipal();

    @Query(value = SELECT_PASARELA + "WHERE p.id = :id", nativeQuery = true)
    Optional<PasarelaView> buscarPorId(@Param("id") Long id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM pasarelas_pago)", nativeQuery = true)
    boolean existeAlguna();

    @Query(value = """
            INSERT INTO pasarelas_pago (nombre_pasarela, url_api, api_key, estado)
            VALUES (:#{#p.nombrePasarela}, :#{#p.urlApi}, :#{#p.apiKey}, :#{#p.estado})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("p") PasarelaPago p);

    @Modifying
    @Query(value = """
            UPDATE pasarelas_pago
            SET nombre_pasarela = :nombre, url_api = :urlApi, api_key = :apiKey, estado = :estado
            WHERE id = :id
            """, nativeQuery = true)
    int actualizar(@Param("id") Long id, @Param("nombre") String nombre, @Param("urlApi") String urlApi,
                   @Param("apiKey") String apiKey, @Param("estado") String estado);
}
