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

    /** Usada por ReservaZonaComun.validarHorario: la hora cae dentro del horario de la zona (admite cruce de medianoche). */
    public boolean horarioIncluye(LocalTime hora) {
        if (hora == null || horaInicio == null || horaFin == null) {
            return false;
        }
        if (horaInicio.isBefore(horaFin)) {
            return !hora.isBefore(horaInicio) && hora.isBefore(horaFin);
        }
        return !hora.isBefore(horaInicio) || hora.isBefore(horaFin);
    }
}
