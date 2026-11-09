package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
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

import java.time.LocalTime;

/**
 * Reserva de una zona comun por un residente.
 * <p>
 * mostrarZonasComunes() y verificarDisponibilidad(hora) estan en la capa de servicios
 * (necesitan contar las reservas existentes en la base de datos).
 */
@Entity
@DynamicUpdate
@Table(name = "reservas_zona_comun")
@Getter
@Setter
@NoArgsConstructor
public class ReservaZonaComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dueno_reserva_id")
    private Residente duenoReserva;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_comun_id")
    private ZonaComun zonaComun;

    public ReservaZonaComun(Long id, Residente duenoReserva, ZonaComun zonaComun) {
        this.id = id;
        this.duenoReserva = duenoReserva;
        this.zonaComun = zonaComun;
    }

    /** validarHorario(hora : LocalTime) : Boolean -> la hora esta dentro del horario de la zona. */
    public Boolean validarHorario(LocalTime hora) {
        return zonaComun != null && zonaComun.horarioIncluye(hora);
    }

    /**
     * estaHabilitado(duenoReserva : Residente) : Boolean.
     * Habilitado = cuenta activa, inmueble activo y sin mora en la administracion.
     */
    public Boolean estaHabilitado(Residente duenoReserva) {
        if (duenoReserva == null || !duenoReserva.isActive()) {
            return false;
        }
        Inmueble inmueble = duenoReserva.getInmueble();
        if (inmueble == null || !Boolean.TRUE.equals(inmueble.getIsActive())) {
            return false;
        }
        Liquidacion vigente = inmueble.getLiquidacion();
        return vigente == null || !(Liquidacion.EN_MORA.equals(vigente.getEstado()) || vigente.estaVencida());
    }
}
