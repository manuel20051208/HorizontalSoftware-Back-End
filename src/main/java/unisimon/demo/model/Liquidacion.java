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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Liquidacion mensual de la administracion de un inmueble.
 * Estados: PENDIENTE (recien generada), AL_DIA (pagada) y EN_MORA (vencida sin pagar).
 * generarLiquidacion(inmueble) esta en la capa de servicios.
 */
@Entity
@DynamicUpdate
@Table(name = "liquidaciones", uniqueConstraints = @UniqueConstraint(columnNames = {"inmueble_id", "periodo"}))
@Getter
@Setter
@NoArgsConstructor
public class Liquidacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String AL_DIA = "AL_DIA";
    public static final String EN_MORA = "EN_MORA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id")
    private Inmueble inmueble;

    /** Primer dia del mes que se cobra. */
    @Column(nullable = false)
    private LocalDate periodo;

    private LocalDate fechaGeneracion;

    private LocalDate fechaLimite;

    @Column(nullable = false)
    private BigDecimal cuotaAdministracion = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal valorServiciosComunes = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal valorMultas = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private String estado = PENDIENTE;

    public Liquidacion(Long id, Inmueble inmueble, LocalDate periodo, LocalDate fechaLimite,
                       BigDecimal cuotaAdministracion, BigDecimal valorServiciosComunes, BigDecimal valorMultas) {
        this.id = id;
        this.inmueble = inmueble;
        this.periodo = periodo;
        this.fechaGeneracion = LocalDate.now();
        this.fechaLimite = fechaLimite;
        this.cuotaAdministracion = nz(cuotaAdministracion);
        this.valorServiciosComunes = nz(valorServiciosComunes);
        this.valorMultas = nz(valorMultas);
        this.estado = PENDIENTE;
        calcularTotal();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** calcularTotal() : BigDecimal = cuota + servicios comunes + multas (y lo deja guardado en total). */
    public BigDecimal calcularTotal() {
        this.total = nz(cuotaAdministracion).add(nz(valorServiciosComunes)).add(nz(valorMultas));
        return this.total;
    }

    /** estaVencida() : Boolean -> paso la fecha limite y no esta pagada. */
    public Boolean estaVencida() {
        return fechaLimite != null && LocalDate.now().isAfter(fechaLimite) && !AL_DIA.equals(estado);
    }

    /** marcarEnMora() */
    public void marcarEnMora() {
        this.estado = EN_MORA;
    }

    /** marcarAlDia() */
    public void marcarAlDia() {
        this.estado = AL_DIA;
    }
}
