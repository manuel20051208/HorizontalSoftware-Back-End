package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Regla de reserva definida por un administrador (anticipacion, duracion y penalidad por cancelar tarde). */
@Entity
@DynamicUpdate
@Table(name = "reglas_reserva")
@Getter
@Setter
@NoArgsConstructor
public class ReglaReserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int anticipacionMinimaHoras;

    private int duracionMaximaHoras;

    private int horasAntelacionPenalidad;

    @Column(nullable = false)
    private BigDecimal tarifaPenalidad = BigDecimal.ZERO;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Administrador admin;

    public ReglaReserva(Long id, int anticipacionMinimaHoras, int duracionMaximaHoras,
                        int horasAntelacionPenalidad, BigDecimal tarifaPenalidad, Administrador admin) {
        this.id = id;
        this.anticipacionMinimaHoras = anticipacionMinimaHoras;
        this.duracionMaximaHoras = duracionMaximaHoras;
        this.horasAntelacionPenalidad = horasAntelacionPenalidad;
        this.tarifaPenalidad = tarifaPenalidad == null ? BigDecimal.ZERO : tarifaPenalidad;
        this.admin = admin;
    }

    /** actualizarReglas(nuevaAnticipacion : int, nuevaDuracion : int) */
    public void actualizarReglas(int nuevaAnticipacion, int nuevaDuracion) {
        if (nuevaAnticipacion < 0) {
            throw new IllegalArgumentException("La anticipacion minima no puede ser negativa");
        }
        if (nuevaDuracion <= 0) {
            throw new IllegalArgumentException("La duracion maxima debe ser mayor que cero");
        }
        this.anticipacionMinimaHoras = nuevaAnticipacion;
        this.duracionMaximaHoras = nuevaDuracion;
    }
}
