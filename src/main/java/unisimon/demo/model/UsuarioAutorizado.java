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
 * UsuarioAutorizado: invitado de un residente. Segun el diagrama hereda de Residente
 * (y por tanto de Usuario) e implementa NoAdmin.
 * Operacion del diagrama: buscarRelacionResidente() -> capa de servicios.
 */
@Entity
@DynamicUpdate
@DiscriminatorValue(Usuario.USUARIO_AUTORIZADO)
@Getter
@Setter
@NoArgsConstructor
public class UsuarioAutorizado extends Residente implements NoAdmin {

    @Column(name = "vence_autorizacion")
    private LocalDate venceAutorizacion;

    public UsuarioAutorizado(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
                             String correoElectronico, String contrasena, String tipoUsuario,
                             Inmueble inmueble, Auto auto, LocalDate venceAutorizacion) {
        super(id, identificacion, nombreCompleto, fechaNacimiento, correoElectronico, contrasena, tipoUsuario,
                inmueble, auto);
        this.venceAutorizacion = venceAutorizacion;
    }

    /** La autorizacion vale hasta el dia de vencimiento, inclusive. Sin fecha = permanente. */
    public boolean autorizacionVigente() {
        return venceAutorizacion == null || !LocalDate.now().isAfter(venceAutorizacion);
    }
}
