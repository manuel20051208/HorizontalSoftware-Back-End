package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
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

/**
 * Zona comun (salon social, gimnasio, BBQ...). Las reglas de reserva se le asignan mediante {@link Rompimiento}.
 * <p>
 * Tal como esta en el diagrama de clases:
 * <ul>
 *   <li>hereda de {@link Inmueble}: {@code id} e {@code isActive} son los que hereda. En la base de datos sigue en
 *       su propia tabla ({@code zonas_comunes}); todo el acceso es SQL nativo sobre esa tabla.</li>
 *   <li>{@code horaInicio} y {@code horaFin} son {@code LocalDateTime} y el constructor los recibe como
 *       {@code LocalTime}. Del horario solo cuenta la hora: la fecha es siempre {@link #FECHA_BASE} y en la base
 *       se guarda como TIME.</li>
 * </ul>
 */
@Entity
@DynamicUpdate
@Table(name = "zonas_comunes")
@Getter
@Setter
@NoArgsConstructor
public class ZonaComun extends Inmueble {

    /** Fecha fija que acompana a la hora del horario (el horario es el mismo todos los dias). */
    public static final LocalDate FECHA_BASE = LocalDate.EPOCH;

    @Column(nullable = false, unique = true)
    private String nombreZonaComun;

    private int capacidad;

    @Convert(converter = HoraDeHorario.class)
    private LocalDateTime horaInicio;

    @Convert(converter = HoraDeHorario.class)
    private LocalDateTime horaFin;

    @OneToMany(mappedBy = "zonaComun")
    private List<Rompimiento> reglasAsignadas = new ArrayList<>();

    public ZonaComun(Long id, String nombreZonaComun, int capacidad, LocalTime horaInicio, LocalTime horaFin) {
        setId(id);
        this.nombreZonaComun = nombreZonaComun;
        this.capacidad = capacidad;
        this.horaInicio = horaInicio == null ? null : FECHA_BASE.atTime(horaInicio);
        this.horaFin = horaFin == null ? null : FECHA_BASE.atTime(horaFin);
        setIsActive(true);
    }

    /** cambiarEstado(): activa la zona si estaba inactiva y viceversa. */
    public void cambiarEstado() {
        setIsActive(!Boolean.TRUE.equals(getIsActive()));
    }

    /** La hora cae dentro del horario de la zona (admite cruce de medianoche). */
    public boolean horarioIncluye(LocalTime hora) {
        if (hora == null || this.horaInicio == null || this.horaFin == null) {
            return false;
        }
        LocalTime horaInicio = this.horaInicio.toLocalTime();
        LocalTime horaFin = this.horaFin.toLocalTime();
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
        if (inicio == null || fin == null || this.horaInicio == null || this.horaFin == null || !fin.isAfter(inicio)) {
            return false;
        }
        LocalTime horaInicio = this.horaInicio.toLocalTime();
        LocalTime horaFin = this.horaFin.toLocalTime();
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

    /** La columna es TIME: de la fecha y hora del atributo solo se guarda la hora. */
    @Converter
    public static class HoraDeHorario implements AttributeConverter<LocalDateTime, LocalTime> {

        @Override
        public LocalTime convertToDatabaseColumn(LocalDateTime atributo) {
            return atributo == null ? null : atributo.toLocalTime();
        }

        @Override
        public LocalDateTime convertToEntityAttribute(LocalTime columna) {
            return columna == null ? null : FECHA_BASE.atTime(columna);
        }
    }
}
