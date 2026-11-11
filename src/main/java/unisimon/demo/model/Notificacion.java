package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Notificacion para uno o varios usuarios. Tipos: GENERAL, RESERVA, PAGO_PENDIENTE, PAGO_ACTUALIZADO, VISITA.
 * Los metodos notificarX arman destinatarios y mensaje y luego llaman a {@link #enviarNotificacion()};
 * guardarla en la base de datos lo hace la capa de servicios.
 */
@Entity
@DynamicUpdate
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(name = "notificacion_usuarios",
            joinColumns = @JoinColumn(name = "notificacion_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private List<Usuario> usuarios = new ArrayList<>();

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(nullable = false)
    private String tipoNotificacion;

    private LocalDateTime fechaEnvio;

    public Notificacion(Long id, List<Usuario> usuarios, String mensaje, String tipoNotificacion,
                        LocalDateTime fechaEnvio) {
        this.id = id;
        this.usuarios = usuarios == null ? new ArrayList<>() : new ArrayList<>(usuarios);
        this.mensaje = mensaje;
        this.tipoNotificacion = tipoNotificacion;
        this.fechaEnvio = fechaEnvio;
    }

    /** enviarNotificacion() : Boolean -> la entrega a todos los destinatarios y les marca "sin leer". */
    public Boolean enviarNotificacion() {
        if (usuarios == null || usuarios.isEmpty() || mensaje == null || mensaje.isBlank()) {
            return false;
        }
        this.fechaEnvio = LocalDateTime.now();
        usuarios.forEach(u -> u.setNotificacionLeida(false));
        return true;
    }

    /** notificarReserva(reserva : ReservaZonaComun) */
    public void notificarReserva(ReservaZonaComun reserva) {
        this.tipoNotificacion = "RESERVA";
        this.usuarios = new ArrayList<>(List.of(reserva.getDuenoReserva()));
        this.mensaje = "Su reserva de " + reserva.getZonaComun().getNombreZonaComun() + cuando(reserva)
                + " quedo registrada.";
        enviarNotificacion();
    }

    /** notificarPagoPendiente(inmueble : Inmueble) */
    public void notificarPagoPendiente(Inmueble inmueble) {
        this.tipoNotificacion = "PAGO_PENDIENTE";
        this.usuarios = new ArrayList<>(inmueble.residentesActivos());
        Liquidacion l = inmueble.getLiquidacion();
        this.mensaje = l == null
                ? "Tiene un pago de administracion pendiente."
                : "Tiene un pago de administracion pendiente por $ " + pesos(l.getTotal())
                + (l.getFechaLimite() == null ? "." : " con vencimiento el " + l.getFechaLimite() + ".");
        enviarNotificacion();
    }

    /** notificarActualizacionDePago(inmueble : Inmueble, usuarios : List&lt;Usuario&gt;) */
    public void notificarActualizacionDePago(Inmueble inmueble, List<Usuario> usuarios) {
        this.tipoNotificacion = "PAGO_ACTUALIZADO";
        this.usuarios = new ArrayList<>(usuarios != null && !usuarios.isEmpty() ? usuarios : inmueble.residentesActivos());
        this.mensaje = "Se actualizo el estado del pago de administracion del inmueble " + inmueble.getDireccionInmueble() + ".";
        enviarNotificacion();
    }

    /** notificarVisita(inmueble : Inmueble) */
    public void notificarVisita(Inmueble inmueble) {
        this.tipoNotificacion = "VISITA";
        this.usuarios = new ArrayList<>(inmueble.residentesActivos());
        this.mensaje = "Ingreso una visita autorizada con destino a su inmueble " + inmueble.getDireccionInmueble() + ".";
        enviarNotificacion();
    }

    private static String pesos(BigDecimal valor) {
        return NumberFormat.getIntegerInstance(Locale.of("es", "CO")).format(valor);
    }

    /** " para el 2026-11-20 de 10:00 a 12:00" (o "hasta el <fecha> <hora>" si termina otro dia); vacio sin fechas. */
    private static String cuando(ReservaZonaComun reserva) {
        LocalDateTime inicio = reserva.getFechaHoraInicio();
        LocalDateTime fin = reserva.getFechaHoraFin();
        if (inicio == null || fin == null) {
            return "";
        }
        String hasta = inicio.toLocalDate().equals(fin.toLocalDate())
                ? " a " + fin.toLocalTime()
                : " hasta el " + fin.toLocalDate() + " " + fin.toLocalTime();
        return " para el " + inicio.toLocalDate() + " de " + inicio.toLocalTime() + hasta;
    }
}
