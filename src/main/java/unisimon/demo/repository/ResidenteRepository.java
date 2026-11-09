package unisimon.demo.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import unisimon.demo.model.Residente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Lecturas de contexto de un residente: su cuenta, su inmueble y la liquidacion vigente. SQL nativo. */
public interface ResidenteRepository extends Repository<Residente, Long> {

    interface ContextoResidenteView {
        Long getId();

        String getNombreCompleto();

        String getTipoUsuario();

        Boolean getActive();

        Long getInmuebleId();

        String getDireccionInmueble();

        Boolean getInmuebleActive();

        Long getLiquidacionId();

        String getLiquidacionEstado();

        LocalDate getLiquidacionFechaLimite();

        BigDecimal getLiquidacionTotal();
    }

    @Query(value = """
            SELECT u.id AS "id", u.nombre_completo AS "nombreCompleto", u.tipo_usuario AS "tipoUsuario",
                   u.active AS "active", i.id AS "inmuebleId", i.direccion_inmueble AS "direccionInmueble",
                   i.is_active AS "inmuebleActive", l.id AS "liquidacionId", l.estado AS "liquidacionEstado",
                   l.fecha_limite AS "liquidacionFechaLimite", l.total AS "liquidacionTotal"
            FROM usuarios u
            JOIN inmuebles i ON i.id = u.inmueble_id
            LEFT JOIN liquidaciones l ON l.id = i.liquidacion_actual_id
            WHERE u.id = :id AND u.tipo_usuario IN ('RESIDENTE', 'USUARIO_AUTORIZADO')
            """, nativeQuery = true)
    Optional<ContextoResidenteView> buscarContexto(@Param("id") Long id);
}
