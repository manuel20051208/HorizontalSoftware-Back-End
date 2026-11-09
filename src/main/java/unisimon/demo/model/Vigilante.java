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
 * Vigilante (hereda de Usuario e implementa NoAdmin).
 * Sus operaciones (verificarAccesoZonaComun, existeInvitado, consultarReservas) estan en la capa de servicios.
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
}
