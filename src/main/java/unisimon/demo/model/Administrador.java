package unisimon.demo.model;

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
 * Sus operaciones del diagrama (modificarActive, gestionarCuentaUsuario, configurarMultas, configurarTarifas,
 * ConfigurarZonasComunes, ConfigurarNuevaReglaReserva, conciliarPagosAdministracion y
 * consultarConfirmacionLectura) estan en la capa de servicios, repartidas por responsabilidad.
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
}
