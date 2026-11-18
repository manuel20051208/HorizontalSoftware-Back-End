package unisimon.demo.model;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Vigilante (hereda de Usuario e implementa NoAdmin).
 * Sus operaciones del diagrama estan aqui con su firma. Trabajan sobre las reservas e invitados que reciben por
 * parametro; leer y guardar en la base de datos lo hace la capa de servicios.
 */
@Entity
@DynamicUpdate
@DiscriminatorValue(Usuario.VIGILANTE)
@Getter
@Setter
@NoArgsConstructor
public class Vigilante extends Usuario implements NoAdmin {

    @Column(name = "vigilante_inicio_contrato")
    private LocalDate inicioContrato;

    @Column(name = "vigilante_vence_contrato")
    private LocalDate venceContrato;

    public Vigilante(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
                     String correoElectronico, String contrasena, String tipoUsuario,
                     LocalDate inicioContrato, LocalDate venceContrato) {
        super(id, identificacion, nombreCompleto, fechaNacimiento, correoElectronico, contrasena, tipoUsuario);
        this.inicioContrato = inicioContrato;
        this.venceContrato = venceContrato;
    }

    public boolean contratoVigente() {
        LocalDate hoy = LocalDate.now();
        return (inicioContrato == null || !hoy.isBefore(inicioContrato))
                && (venceContrato == null || !hoy.isAfter(venceContrato));
    }

    /**
     * verificarAccesoZonaComun(usuario : Usuario, reservas : List&lt;ReservaZonaComun&gt;,
     * invitados : List&lt;UsuarioAutorizado&gt;) : Boolean.
     * Entra quien tiene la cuenta activa y, ahora mismo, una reserva EN CURSO de una zona activa: suya o, si es
     * un invitado registrado con la autorizacion vigente, de alguno de los residentes que lo autorizaron.
     */
    public Boolean verificarAccesoZonaComun(Usuario usuario, List<ReservaZonaComun> reservas,
                                            List<UsuarioAutorizado> invitados) {
        if (usuario == null || !usuario.isActive()) {
            return false;
        }
        List<Long> duenos = new ArrayList<>();
        duenos.add(usuario.getId());
        if (usuario instanceof UsuarioAutorizado invitado) {
            if (!existeInvitado(invitado.getIdentificacion(), invitados) || !invitado.autorizacionVigente()) {
                return false;
            }
            invitado.buscarRelacionResidente().forEach(r -> duenos.add(r.getId()));
        }
        LocalDateTime ahora = LocalDateTime.now();
        return reservas != null && reservas.stream().anyMatch(r -> r.getDuenoReserva() != null
                && duenos.contains(r.getDuenoReserva().getId())
                && r.getZonaComun() != null && Boolean.TRUE.equals(r.getZonaComun().getIsActive())
                && r.intervaloValido() && ReservaZonaComun.EN_CURSO.equals(r.estado(ahora)));
    }

    /** existeInvitado(identificacion : Long, invitados : List&lt;UsuarioAutorizado&gt;) : Boolean */
    public Boolean existeInvitado(Long identificacion, List<UsuarioAutorizado> invitados) {
        return identificacion != null && invitados != null
                && invitados.stream().anyMatch(i -> identificacion.equals(i.getIdentificacion()));
    }

    /**
     * consultarReservas(reservas : List&lt;ReservaZonaComun&gt;) : List&lt;ReservaZonaComun&gt; -> las reservas que
     * ve la porteria (copia inmutable; vacia si no recibe ninguna).
     */
    public List<ReservaZonaComun> consultarReservas(List<ReservaZonaComun> reservas) {
        return reservas == null ? List.of() : List.copyOf(reservas);
    }

    /**
     * consultarInvitados(invitados : List&lt;UsuarioAutorizado&gt;) : List&lt;UsuarioAutorizado&gt; -> los
     * invitados registrados (copia inmutable; vacia si no recibe ninguno).
     */
    public List<UsuarioAutorizado> consultarInvitados(List<UsuarioAutorizado> invitados) {
        return invitados == null ? List.of() : List.copyOf(invitados);
    }

    /**
     * registrarIncidencia(descripcion : String): solo un vigilante activo y con contrato vigente registra
     * incidencias, y la descripcion es obligatoria. La notificacion INCIDENCIA la arma y envia el servicio.
     */
    public void registrarIncidencia(String descripcion) {
        if (!isActive() || !contratoVigente()) {
            throw new IllegalStateException("El vigilante no esta activo o su contrato no esta vigente");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion de la incidencia es obligatoria");
        }
    }
}
