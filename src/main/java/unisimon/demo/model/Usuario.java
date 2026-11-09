package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
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

/**
 * Usuario (diagrama de clases). Clase padre de Administrador, Residente, Vigilante y UsuarioAutorizado.
 * <p>
 * {@code contrasena} es del tipo PasswordEncoder del diagrama: aqui se guarda SIEMPRE la contrasena ya
 * codificada (BCrypt); nunca el texto plano.
 * <p>
 * Las operaciones del diagrama que necesitan base de datos (iniciarSesion, cambiarContrasena, activarCuenta,
 * recuperarContrasena, marcarNotificacionLeida) viven en la capa de servicios.
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

    /** Parte de marcarNotificacionLeida(...) que no necesita base de datos. */
    public void marcarNotificacionLeida() {
        this.notificacionLeida = true;
    }
}
