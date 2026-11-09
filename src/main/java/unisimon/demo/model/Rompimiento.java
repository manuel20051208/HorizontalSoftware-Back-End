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

import java.time.LocalDate;

/** Asignacion de una {@link ReglaReserva} a una {@link ZonaComun} (clase de union del diagrama). */
@Entity
@DynamicUpdate
@Table(name = "rompimientos")
@Getter
@Setter
@NoArgsConstructor
public class Rompimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_comun_id")
    private ZonaComun zonaComun;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "regla_reserva_id")
    private ReglaReserva reglaReserva;

    private LocalDate fechaAplicacion;

    @Column(nullable = false)
    private Boolean activa = true;

    @Column(length = 500)
    private String observaciones;

    public Rompimiento(Long id, ZonaComun zonaComun, ReglaReserva reglaReserva,
                       LocalDate fechaAplicacion, String observaciones) {
        this.id = id;
        this.zonaComun = zonaComun;
        this.reglaReserva = reglaReserva;
        this.fechaAplicacion = fechaAplicacion;
        this.observaciones = observaciones;
        this.activa = true;
    }

    /** aplicarRegla(): deja la regla vigente desde hoy. */
    public void aplicarRegla() {
        this.activa = true;
        this.fechaAplicacion = LocalDate.now();
    }

    /** inactivarRegla(): la regla deja de aplicar a la zona. */
    public void inactivarRegla() {
        this.activa = false;
    }
}
