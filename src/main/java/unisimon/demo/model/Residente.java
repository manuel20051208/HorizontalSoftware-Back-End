package unisimon.demo.model;

import java.time.LocalDateTime;
import jakarta.persistence.Transient;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Residente (hereda de Usuario e implementa NoAdmin).
 * <p>
 * Sus operaciones del diagrama estan aqui con su firma. Trabajan sobre su inmueble (liquidacion, pagos, zonas
 * y notificaciones ya cargados), sus invitados y el contexto que deja el servicio en {@code reservas}; leer y
 * guardar en la base de datos lo hace la capa de servicios.
 */
@Entity
@DynamicUpdate
@DiscriminatorValue(Usuario.RESIDENTE)
@Getter
@Setter
@NoArgsConstructor
public class Residente extends Usuario implements NoAdmin {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id")
    private Inmueble inmueble;

    @ManyToMany
    @JoinTable(name = "residente_invitados",
            joinColumns = @JoinColumn(name = "residente_id"),
            inverseJoinColumns = @JoinColumn(name = "invitado_id"))
    private List<UsuarioAutorizado> invitados = new ArrayList<>();

    /** Lado inverso: el dueno de la relacion es {@link Auto#getDueno()}. */
    @OneToOne(mappedBy = "dueno", fetch = FetchType.LAZY)
    private Auto auto;

    /** Contexto (no es atributo del diagrama ni se guarda): las reservas de este residente. */
    @Transient
    private List<ReservaZonaComun> reservas = new ArrayList<>();

    public Residente(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
                     String correoElectronico, String contrasena, String tipoUsuario,
                     Inmueble inmueble, Auto auto) {
        super(id, identificacion, nombreCompleto, fechaNacimiento, correoElectronico, contrasena, tipoUsuario);
        this.inmueble = inmueble;
        this.auto = auto;
    }

    public boolean tieneInvitado(UsuarioAutorizado invitado) {
        return invitado != null && invitados.stream().anyMatch(i -> i.getId().equals(invitado.getId()));
    }

    /** verReservas(id : Long) : List&lt;ReservaZonaComun&gt; -> las reservas del residente {@code id} (este). */
    public List<ReservaZonaComun> verReservas(Long id) {
        exigirPropio(id);
        return List.copyOf(reservas);
    }

    /**
     * cambiarEstadoInvitado(invitado : UsuarioAutorizado, id : Long) : Boolean.
     * Activa o desactiva al invitado {@code id}; solo si es un invitado de este residente. Devuelve si lo cambio.
     */
    public Boolean cambiarEstadoInvitado(UsuarioAutorizado invitado, Long id) {
        if (invitado == null || id == null || !id.equals(invitado.getId()) || !tieneInvitado(invitado)) {
            return false;
        }
        invitado.setActive(!invitado.isActive());
        return true;
    }

    /**
     * cambiarEstadoInvitados(invitados : List&lt;UsuarioAutorizado&gt;) : Boolean.
     * Activa o desactiva varios invitados a la vez. Si alguno no es de este residente no cambia ninguno.
     */
    public Boolean cambiarEstadoInvitados(List<UsuarioAutorizado> invitados) {
        if (invitados == null || invitados.isEmpty() || !invitados.stream().allMatch(this::tieneInvitado)) {
            return false;
        }
        invitados.forEach(i -> i.setActive(!i.isActive()));
        return true;
    }

    /**
     * consultarEstadoCuenta(inmueble : Inmueble, id : Long) : Liquidacion.
     * La liquidacion vigente del inmueble del residente {@code id} (este), con su total recalculado; null si
     * el inmueble aun no tiene liquidaciones.
     */
    public Liquidacion consultarEstadoCuenta(Inmueble inmueble, Long id) {
        exigirPropio(id);
        exigirInmueble(inmueble);
        Liquidacion vigente = inmueble.getLiquidacion();
        if (vigente != null) {
            vigente.calcularTotal();
        }
        return vigente;
    }

    /** consultarHistorialPago(id : Long) : List&lt;Pago&gt; -> los pagos registrados del inmueble del residente. */
    public List<Pago> consultarHistorialPago(Long id) {
        exigirPropio(id);
        return inmueble == null ? List.of() : List.copyOf(inmueble.getRegistroPagos());
    }

    /**
     * reservarZonaComun(id : Long) : ZonaComun.
     * Devuelve la zona comun {@code id} que el residente puede reservar: tiene que estar activa y asignada a su
     * inmueble, y el residente habilitado (cuenta e inmueble activos y sin mora). Cupo, horario y reglas del
     * intervalo los valida ReservaZonaComun.
     */
    public ZonaComun reservarZonaComun(Long id) {
        ZonaComun zona = inmueble == null ? null : inmueble.getZonaComunes().stream()
                .filter(z -> id != null && id.equals(z.getId()) && Boolean.TRUE.equals(z.getIsActive()))
                .findFirst().orElse(null);
        if (zona == null) {
            throw new IllegalArgumentException("La zona comun no existe, esta inactiva o no esta asignada a su inmueble");
        }
        if (!new ReservaZonaComun(null, this, zona).estaHabilitado(this)) {
            throw new IllegalStateException("No esta habilitado para reservar: cuenta o inmueble inactivo, o en mora");
        }
        return zona;
    }

    /**
     * cancelarReservaZonaComun(idAdmin : Long): cancela la reserva con ese id (el diagrama llama idAdmin al
     * parametro). Tiene que ser de este residente y no haber empezado.
     */
    public void cancelarReservaZonaComun(Long idAdmin) {
        ReservaZonaComun reserva = reservas.stream()
                .filter(r -> idAdmin != null && idAdmin.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La reserva no existe o no es suya"));
        if (!reserva.sePuedeCancelar(LocalDateTime.now())) {
            throw new IllegalStateException("Solo se puede cancelar una reserva que aun no ha empezado");
        }
        reservas.remove(reserva);
    }

    /**
     * consultarNotificacion(inmueble : Inmueble, id : Long) : List&lt;Notificacion&gt;.
     * Las notificaciones del inmueble del residente {@code id} (este).
     */
    public List<Notificacion> consultarNotificacion(Inmueble inmueble, Long id) {
        exigirPropio(id);
        exigirInmueble(inmueble);
        return List.copyOf(inmueble.getNotificaciones());
    }

    // ------------------------------------------------------------------ apoyo (no son operaciones del diagrama)

    @Override
    protected boolean perteneceA(Inmueble otro) {
        if (otro == null || inmueble == null) {
            return false;
        }
        return otro == inmueble || (otro.getId() != null && otro.getId().equals(inmueble.getId()));
    }

    private void exigirPropio(Long id) {
        if (!esEste(id)) {
            throw new IllegalArgumentException("La consulta debe hacerla el propio residente");
        }
    }

    private void exigirInmueble(Inmueble otro) {
        if (!perteneceA(otro)) {
            throw new IllegalArgumentException("El inmueble no es el del residente");
        }
    }
}
