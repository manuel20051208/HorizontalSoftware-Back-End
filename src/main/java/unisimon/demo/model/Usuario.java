package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Usuario (diagrama de clases). Clase padre de Administrador, Residente, Vigilante y UsuarioAutorizado.
 * <p>
 * {@code contrasena} es del tipo PasswordEncoder del diagrama: aqui se guarda SIEMPRE la contrasena ya
 * codificada (BCrypt); nunca el texto plano.
 * <p>
 * Todas las operaciones del diagrama estan aqui con su firma. Deciden con los datos que ya trae el objeto;
 * leer y guardar en la base de datos lo hace la capa de servicios, antes y despues de llamarlas.
 * <p>
 * En iniciarSesion y activarCuenta el parametro {@code contrasena} es la contrasena YA codificada (la que el
 * servicio verifico con el PasswordEncoder): la entidad nunca ve el texto plano.
 * <p>
 * {@code preferenciasNotificacion} son los tipos de notificacion que el usuario quiere recibir; la lista vacia
 * significa "sin preferencias" y entonces recibe todos.
 */
@Entity
@DynamicUpdate
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String RESIDENTE = "RESIDENTE";
    public static final String VIGILANTE = "VIGILANTE";
    public static final String USUARIO_AUTORIZADO = "USUARIO_AUTORIZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long identificacion;

    @Column(nullable = false)
    private String nombreCompleto;

    private LocalDate fechaNacimiento;

    @Column(nullable = false, unique = true)
    private String correoElectronico;

    /** Contrasena codificada (PasswordEncoder). */
    @Column(nullable = false)
    private String contrasena;

    private LocalDate fechaCreacionCuenta;

    /** ADMINISTRADOR, RESIDENTE, VIGILANTE o USUARIO_AUTORIZADO (columna discriminadora de la tabla usuarios). */
    @Column(name = "tipo_usuario", insertable = false, updatable = false)
    private String tipoUsuario;

    /** Token de la sesion abierta; null cuando no hay sesion. */
    private String token;

    @Column(nullable = false)
    private Boolean active = false;

    @Column(nullable = false)
    private Boolean notificacionLeida = false;

    /** Tipos de notificacion que el usuario quiere recibir; vacia = todos. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferencias_notificacion", nullable = false)
    private List<String> preferenciasNotificacion = new ArrayList<>();

    public Usuario(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
                   String correoElectronico, String contrasena, String tipoUsuario) {
        this.id = id;
        this.identificacion = identificacion;
        this.nombreCompleto = nombreCompleto;
        this.fechaNacimiento = fechaNacimiento;
        this.correoElectronico = correoElectronico;
        this.contrasena = contrasena;
        this.tipoUsuario = tipoUsuario;
        this.fechaCreacionCuenta = LocalDate.now();
        this.active = false;
        this.notificacionLeida = false;
    }

    /** isActive() : Boolean */
    public Boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    /** cerrarSesion(): invalida el token de la sesion. */
    public void cerrarSesion() {
        this.token = null;
    }

    /**
     * iniciarSesion(correo : String, contrasena : PasswordEncoder) : usuario.
     * Devuelve este usuario si la cuenta esta activa y las credenciales son las suyas; si no, null.
     */
    public Usuario iniciarSesion(String correo, String contrasena) {
        return isActive() && credencialesCorrectas(correo, contrasena) ? this : null;
    }

    /**
     * cambiarContrasena(inmueble : Inmueble, id : Long): el usuario {@code id} (este) cambia su contrasena.
     * Exige cuenta activa y, si es residente, que {@code inmueble} sea el suyo. Cierra la sesion abierta;
     * la contrasena nueva ya codificada la deja el servicio con setContrasena.
     */
    public void cambiarContrasena(Inmueble inmueble, Long id) {
        if (!esEste(id) || !isActive() || !perteneceA(inmueble)) {
            throw new IllegalStateException("No se puede cambiar la contrasena de esta cuenta");
        }
        cerrarSesion();
    }

    /**
     * activarCuenta(correo : String, contrasena : PasswordEncoder) : Boolean.
     * Activa la cuenta si las credenciales son correctas y aun estaba inactiva; si no, devuelve false.
     */
    public Boolean activarCuenta(String correo, String contrasena) {
        if (isActive() || !credencialesCorrectas(correo, contrasena)) {
            return false;
        }
        this.active = true;
        return true;
    }

    /** recuperarContrasena(): invalida la sesion; la contrasena temporal la genera y envia el servicio. */
    public void recuperarContrasena() {
        cerrarSesion();
    }

    /**
     * marcarNotificacionLeida(inmueble : Inmueble, id : Long): el usuario {@code id} (este) marca como leidas
     * sus notificaciones. Si es residente, {@code inmueble} debe ser el suyo.
     */
    public void marcarNotificacionLeida(Inmueble inmueble, Long id) {
        if (!esEste(id) || !perteneceA(inmueble)) {
            throw new IllegalArgumentException("Solo el propio usuario puede marcar sus notificaciones como leidas");
        }
        this.notificacionLeida = true;
    }

    /**
     * configurarPreferencias(tipoNotificacion : String): alterna el tipo en las preferencias del usuario. Si no
     * estaba, lo agrega (quiere recibirlo); si ya estaba, lo quita. Solo las cuentas activas pueden configurarlas.
     */
    public void configurarPreferencias(String tipoNotificacion) {
        if (!isActive()) {
            throw new IllegalStateException("La cuenta esta inactiva: no puede configurar sus preferencias");
        }
        if (tipoNotificacion == null || tipoNotificacion.isBlank()) {
            throw new IllegalArgumentException("El tipo de notificacion es obligatorio");
        }
        String tipo = tipoNotificacion.trim().toUpperCase(Locale.ROOT);
        if (preferenciasNotificacion == null) {
            preferenciasNotificacion = new ArrayList<>();
        }
        if (!preferenciasNotificacion.remove(tipo)) {
            preferenciasNotificacion.add(tipo);
        }
    }

    /** Recibe este tipo de notificacion? Sin preferencias configuradas los recibe todos. */
    public boolean recibe(String tipoNotificacion) {
        return preferenciasNotificacion == null || preferenciasNotificacion.isEmpty()
                || preferenciasNotificacion.contains(tipoNotificacion);
    }

    // ------------------------------------------------------------------ apoyo (no son operaciones del diagrama)

    /** El id es el de este usuario. */
    protected boolean esEste(Long id) {
        return id != null && id.equals(this.id);
    }

    /** Este usuario pertenece al inmueble. Solo aplica a los residentes; para el resto siempre es cierto. */
    protected boolean perteneceA(Inmueble inmueble) {
        return true;
    }

    private boolean credencialesCorrectas(String correo, String contrasena) {
        return correo != null && correoElectronico != null && correoElectronico.equalsIgnoreCase(correo.trim())
                && contrasena != null && contrasena.equals(this.contrasena);
    }
}
