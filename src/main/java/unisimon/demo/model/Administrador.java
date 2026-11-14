package unisimon.demo.model;

import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.Transient;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Administrador (hereda de Usuario).
 * <p>
 * Sus operaciones del diagrama estan aqui con su firma. Trabajan sobre los objetos que reciben (el inmueble
 * con su liquidacion, zonas y pagos ya cargados) y sobre el contexto que deja el servicio en {@code cuentas} y
 * {@code notificaciones}; leer y guardar en la base de datos lo hace la capa de servicios.
 */
@Entity
@DynamicUpdate
@DiscriminatorValue(Usuario.ADMINISTRADOR)
@Getter
@Setter
@NoArgsConstructor
public class Administrador extends Usuario {

    @Column(name = "admin_inicio_contrato")
    private LocalDate inicioContrato;

    @Column(name = "admin_vence_contrato")
    private LocalDate venceContrato;

    /** Contexto (no es atributo del diagrama ni se guarda): las cuentas sobre las que opera modificarActive. */
    @Transient
    private List<Usuario> cuentas = new ArrayList<>();

    /** Contexto (no es atributo del diagrama ni se guarda): las notificaciones que consulta el administrador. */
    @Transient
    private List<Notificacion> notificaciones = new ArrayList<>();

    public Administrador(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
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
     * modificarActive(identificacion : Long, idToken : Long) : NoAdmin.
     * El administrador {@code idToken} (este) activa o desactiva la cuenta con esa identificacion. Al
     * desactivarla tambien le cierra la sesion. Un administrador no se puede modificar por aqui.
     */
    public NoAdmin modificarActive(Long identificacion, Long idToken) {
        exigirAutoridad(idToken);
        Usuario cuenta = cuentas.stream()
                .filter(u -> identificacion != null && identificacion.equals(u.getIdentificacion()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe un usuario con esa identificacion"));
        if (!(cuenta instanceof NoAdmin noAdmin)) {
            throw new IllegalStateException("Un administrador no se puede activar ni desactivar desde aqui");
        }
        cuenta.setActive(!cuenta.isActive());
        if (!cuenta.isActive()) {
            cuenta.cerrarSesion();
        }
        return noAdmin;
    }

    /** gestionarCuentaUsuario(): solo un administrador activo y con contrato vigente gestiona cuentas. */
    public void gestionarCuentaUsuario() {
        exigirVigente();
    }

    /**
     * configurarMultas(inmueble : Inmueble, adminId : Long): con el nuevo valor de multas ya puesto en la
     * liquidacion vigente del inmueble, recalcula su total.
     */
    public void configurarMultas(Inmueble inmueble, Long adminId) {
        exigirAutoridad(adminId);
        liquidacionVigente(inmueble).calcularTotal();
    }

    /**
     * configurarTarifas(inmueble : Inmueble, adminId : Long): con la nueva cuota y los nuevos servicios comunes
     * ya puestos en la liquidacion vigente del inmueble, recalcula su total.
     */
    public void configurarTarifas(Inmueble inmueble, Long adminId) {
        exigirAutoridad(adminId);
        liquidacionVigente(inmueble).calcularTotal();
    }

    /**
     * ConfigurarZonasComunes(inmueble : Inmueble, adminId : Long): cambia el estado (activa/inactiva) de cada
     * zona comun que trae el inmueble.
     */
    public void ConfigurarZonasComunes(Inmueble inmueble, Long adminId) {
        exigirAutoridad(adminId);
        inmueble.getZonaComunes().forEach(ZonaComun::cambiarEstado);
    }

    /**
     * ConfigurarNuevaReglaReserva(inmueble : Inmueble, adminId : Long): aplica a las zonas comunes del inmueble
     * las reglas de reserva de este administrador que tengan asignadas.
     */
    public void ConfigurarNuevaReglaReserva(Inmueble inmueble, Long adminId) {
        exigirAutoridad(adminId);
        for (ZonaComun zona : inmueble.getZonaComunes()) {
            for (Rompimiento asignacion : zona.getReglasAsignadas()) {
                Administrador autor = asignacion.getReglaReserva() == null ? null : asignacion.getReglaReserva().getAdmin();
                if (autor == null || esEste(autor.getId())) {
                    asignacion.aplicarRegla();
                }
            }
        }
    }

    /**
     * conciliarPagosAdministracion(inmueble : Inmueble, adminId : Long) : Pago.
     * Concilia el pago PENDIENTE mas reciente del inmueble: si su monto es igual al total de la liquidacion
     * vigente lo aprueba y deja la liquidacion AL_DIA; si no, lo anula y falla.
     */
    public Pago conciliarPagosAdministracion(Inmueble inmueble, Long adminId) {
        exigirAutoridad(adminId);
        Pago pago = null;
        for (Pago candidato : inmueble.getRegistroPagos()) {
            if (Pago.PENDIENTE.equals(candidato.getEstado())) {
                pago = candidato;
            }
        }
        if (pago == null) {
            throw new IllegalStateException("El inmueble no tiene un pago pendiente por conciliar");
        }
        Liquidacion liquidacion = liquidacionVigente(inmueble);
        liquidacion.calcularTotal();
        if (!pago.validarMonto(liquidacion)) {
            pago.anularPago();
            throw new IllegalArgumentException("El monto debe ser igual al saldo de la liquidacion");
        }
        pago.setEstado(Pago.APROBADO);
        pago.actualizarEstado(pago);
        return pago;
    }

    /**
     * consultarConfirmacionLectura: revisa quienes leyeron las notificaciones. La lectura de cada destinatario
     * esta en {@code notificaciones} (Notificacion.usuarios y Usuario.notificacionLeida); aqui se comprueba que
     * quien consulta es un administrador activo.
     */
    public void consultarConfirmacionLectura() {
        exigirVigente();
    }

    /** consultarIncidencias() : List&lt;Notificacion&gt; -> las notificaciones de tipo INCIDENCIA. */
    public List<Notificacion> consultarIncidencias() {
        exigirVigente();
        return notificaciones.stream()
                .filter(n -> Notificacion.INCIDENCIA.equals(n.getTipoNotificacion()))
                .toList();
    }

    /** generarReportes(): solo un administrador activo y con contrato vigente genera los reportes. */
    public void generarReportes() {
        exigirVigente();
    }

    // ------------------------------------------------------------------ apoyo (no son operaciones del diagrama)

    private void exigirVigente() {
        if (!isActive() || !contratoVigente()) {
            throw new IllegalStateException("El administrador no esta activo o su contrato no esta vigente");
        }
    }

    /** El administrador que opera (adminId / idToken) tiene que ser este y estar vigente. */
    private void exigirAutoridad(Long adminId) {
        if (!esEste(adminId)) {
            throw new IllegalStateException("La operacion debe hacerla el propio administrador");
        }
        exigirVigente();
    }

    private static Liquidacion liquidacionVigente(Inmueble inmueble) {
        if (inmueble == null || inmueble.getLiquidacion() == null) {
            throw new IllegalStateException("El inmueble no tiene una liquidacion vigente");
        }
        return inmueble.getLiquidacion();
    }
}
