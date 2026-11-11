package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Zona comun (salon social, gimnasio, BBQ...). Las reglas de reserva se le asignan mediante {@link Rompimiento}. */
@Entity
@DynamicUpdate
@Table(name = "zonas_comunes")
@Getter
@Setter
@NoArgsConstructor
public class ZonaComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombreZonaComun;

    private int capacidad;

    private LocalTime horaInicio;

    private LocalTime horaFin;

    @Column(nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "zonaComun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rompimiento> reglasAsignadas = new ArrayList<>();

    public ZonaComun(Long id, String nombreZonaComun, int capacidad, LocalTime horaInicio, LocalTime horaFin) {
        this.id = id;
        this.nombreZonaComun = nombreZonaComun;
        this.capacidad = capacidad;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.isActive = true;
    }

    /** cambiarEstado(): activa la zona si estaba inactiva y viceversa. */
    public void cambiarEstado() {
        this.isActive = !Boolean.TRUE.equals(this.isActive);
    }

    /** La hora cae dentro del horario de la zona (admite cruce de medianoche). */
    public boolean horarioIncluye(LocalTime hora) {
        if (hora == null || horaInicio == null || horaFin == null) {
            return false;
        }
        if (horaInicio.isBefore(horaFin)) {
            return !hora.isBefore(horaInicio) && hora.isBefore(horaFin);
        }
        return !hora.isBefore(horaInicio) || hora.isBefore(horaFin);
    }

    /**
     * Usada por ReservaZonaComun.validarHorario: el intervalo [inicio, fin] cabe completo en UNA apertura de la zona.
     * Con horario diurno (08:00-22:00) inicio y fin caen el mismo dia; con horario que cruza la medianoche
     * (20:00-02:00) la apertura empieza un dia y termina al siguiente.
     */
    public boolean horarioCubre(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null || horaInicio == null || horaFin == null || !fin.isAfter(inicio)) {
            return false;
        }
        LocalDate dia = inicio.toLocalDate();
        LocalTime hora = inicio.toLocalTime();
        LocalDateTime apertura;
        LocalDateTime cierre;
        if (horaInicio.isBefore(horaFin)) {
            apertura = dia.atTime(horaInicio);
            cierre = dia.atTime(horaFin);
        } else if (!hora.isBefore(horaInicio)) {
            apertura = dia.atTime(horaInicio);
            cierre = dia.plusDays(1).atTime(horaFin);
        } else {
            apertura = dia.minusDays(1).atTime(horaInicio);
            cierre = dia.atTime(horaFin);
        }
        return !inicio.isBefore(apertura) && !fin.isAfter(cierre);
    }
}
