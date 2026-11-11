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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Reserva de una zona comun por un residente durante el intervalo [fechaHoraInicio, fechaHoraFin).
 * <p>
 * El estado no se guarda: se calcula comparando el intervalo con la hora actual ({@link #estado}).
 * Contar el cupo y los solapes necesita la base de datos, asi que eso vive en la capa de servicios.
 */
@Entity
@DynamicUpdate
@Table(name = "reservas_zona_comun")
@Getter
@Setter
@NoArgsConstructor
public class ReservaZonaComun {

    /** Aun no empieza: se puede cancelar. */
    public static final String PROGRAMADA = "PROGRAMADA";
    /** Empezo y no ha terminado. */
    public static final String EN_CURSO = "EN_CURSO";
    /** Ya termino. */
    public static final String FINALIZADA = "FINALIZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dueno_reserva_id")
    private Residente duenoReserva;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_comun_id")
    private ZonaComun zonaComun;

    private LocalDateTime fechaHoraInicio;

    private LocalDateTime fechaHoraFin;

    public ReservaZonaComun(Long id, Residente duenoReserva, ZonaComun zonaComun) {
        this.id = id;
        this.duenoReserva = duenoReserva;
        this.zonaComun = zonaComun;
    }

    public ReservaZonaComun(Long id, Residente duenoReserva, ZonaComun zonaComun,
                            LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin) {
        this(id, duenoReserva, zonaComun);
        this.fechaHoraInicio = fechaHoraInicio;
        this.fechaHoraFin = fechaHoraFin;
    }

    /** estado(ahora): PROGRAMADA antes del inicio, EN_CURSO entre inicio y fin, FINALIZADA desde el fin. */
    public String estado(LocalDateTime ahora) {
        if (ahora.isBefore(fechaHoraInicio)) {
            return PROGRAMADA;
        }
        return ahora.isBefore(fechaHoraFin) ? EN_CURSO : FINALIZADA;
    }

    /** El fin es posterior al inicio. */
    public boolean intervaloValido() {
        return fechaHoraInicio != null && fechaHoraFin != null && fechaHoraFin.isAfter(fechaHoraInicio);
    }

    /** validarHorario(): el intervalo completo cabe en una apertura de la zona. */
    public Boolean validarHorario() {
        return zonaComun != null && zonaComun.horarioCubre(fechaHoraInicio, fechaHoraFin);
    }

    /** Se pide con al menos {@code horas} de anticipacion (regla anticipacionMinimaHoras). Sin regla, siempre. */
    public boolean cumpleAnticipacion(LocalDateTime ahora, Integer horas) {
        return horas == null || !fechaHoraInicio.isBefore(ahora.plusHours(horas));
    }

    /** No dura mas de {@code horas} (regla duracionMaximaHoras). Sin regla, siempre. */
    public boolean cumpleDuracionMaxima(Integer horas) {
        return horas == null || Duration.between(fechaHoraInicio, fechaHoraFin).compareTo(Duration.ofHours(horas)) <= 0;
    }

    /** Solo una reserva que aun no empieza se puede cancelar. */
    public boolean sePuedeCancelar(LocalDateTime ahora) {
        return PROGRAMADA.equals(estado(ahora));
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
