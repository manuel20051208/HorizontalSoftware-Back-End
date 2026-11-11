package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.ReglaReserva;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Reglas de reserva. SQL nativo. */
public interface ReglaReservaRepository extends Repository<ReglaReserva, Long> {

    interface ReglaView {
        Long getId();

        Integer getAnticipacionMinimaHoras();

        Integer getDuracionMaximaHoras();

        Integer getHorasAntelacionPenalidad();

        BigDecimal getTarifaPenalidad();

        Long getAdminId();
    }

    String SELECT_REGLA = """
            SELECT r.id AS "id", r.anticipacion_minima_horas AS "anticipacionMinimaHoras",
                   r.duracion_maxima_horas AS "duracionMaximaHoras",
                   r.horas_antelacion_penalidad AS "horasAntelacionPenalidad",
                   r.tarifa_penalidad AS "tarifaPenalidad", r.admin_id AS "adminId"
            FROM reglas_reserva r
            """;

    @Query(value = """
            INSERT INTO reglas_reserva (anticipacion_minima_horas, duracion_maxima_horas, horas_antelacion_penalidad,
                                        tarifa_penalidad, admin_id)
            VALUES (:#{#r.anticipacionMinimaHoras}, :#{#r.duracionMaximaHoras}, :#{#r.horasAntelacionPenalidad},
                    :#{#r.tarifaPenalidad}, :#{#r.admin.id})
            RETURNING id
            """, nativeQuery = true)
    Long insertar(@Param("r") ReglaReserva r);

    @Query(value = SELECT_REGLA + "WHERE r.id = :id", nativeQuery = true)
    Optional<ReglaView> buscarPorId(@Param("id") Long id);

    @Query(value = SELECT_REGLA + "ORDER BY r.id" + SqlPaginacion.LIMITE_DESPLAZAMIENTO, nativeQuery = true)
    List<ReglaView> listar(@Param("limite") int limite, @Param("desplazamiento") long desplazamiento);

    @Query(value = "SELECT COUNT(*) FROM reglas_reserva", nativeQuery = true)
    long contar();

    @Modifying
    @Query(value = """
            UPDATE reglas_reserva SET anticipacion_minima_horas = :anticipacion, duracion_maxima_horas = :duracion
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarReglas(@Param("id") Long id, @Param("anticipacion") int anticipacion, @Param("duracion") int duracion);
}
