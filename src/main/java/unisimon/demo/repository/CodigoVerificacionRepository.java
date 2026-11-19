package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Usuario;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Acceso a la tabla codigos_verificacion (V9): un codigo de 4 cifras vigente por usuario, cifrado. Solo SQL
 * nativo. El tipo de dominio es Usuario porque Spring Data exige uno; la tabla no tiene entidad propia.
 */
public interface CodigoVerificacionRepository extends Repository<Usuario, Long> {

    /** Lo necesario para verificar un codigo. */
    interface CodigoView {
        String getCodigo();

        Integer getIntentos();

        LocalDateTime getVenceEn();
    }

    @Query(value = """
            SELECT c.codigo AS "codigo", c.intentos AS "intentos", c.vence_en AS "venceEn"
            FROM codigos_verificacion c WHERE c.usuario_id = :usuarioId
            """, nativeQuery = true)
    Optional<CodigoView> buscar(@Param("usuarioId") Long usuarioId);

    /** Crea el codigo del usuario o reemplaza el anterior, reiniciando los intentos. */
    @Modifying
    @Query(value = """
            INSERT INTO codigos_verificacion (usuario_id, codigo, intentos, vence_en)
            VALUES (:usuarioId, :codigo, 0, :venceEn)
            ON CONFLICT (usuario_id) DO UPDATE SET codigo = EXCLUDED.codigo, intentos = 0, vence_en = EXCLUDED.vence_en
            """, nativeQuery = true)
    int guardar(@Param("usuarioId") Long usuarioId, @Param("codigo") String codigo,
                @Param("venceEn") LocalDateTime venceEn);

    @Modifying
    @Query(value = "UPDATE codigos_verificacion SET intentos = intentos + 1 WHERE usuario_id = :usuarioId",
            nativeQuery = true)
    int sumarIntento(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query(value = "DELETE FROM codigos_verificacion WHERE usuario_id = :usuarioId", nativeQuery = true)
    int borrar(@Param("usuarioId") Long usuarioId);
}
