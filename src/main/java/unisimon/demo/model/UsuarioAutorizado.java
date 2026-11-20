package unisimon.demo.model;

import java.util.List;
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
 * (y por tanto de Usuario) e implementa NoAdmin. Puede estar relacionado con varios inmuebles.
 * buscarRelacionResidente() recorre los residentes de todos sus inmuebles, que el servicio deja cargados.
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
                             List<Inmueble> inmuebles, Auto auto, LocalDate venceAutorizacion) {
        super(id, identificacion, nombreCompleto, fechaNacimiento, correoElectronico, contrasena, tipoUsuario,
                inmuebles, auto);
        this.venceAutorizacion = venceAutorizacion;
    }

    /** La autorizacion vale hasta el dia de vencimiento, inclusive. Sin fecha = permanente. */
    public boolean autorizacionVigente() {
        return venceAutorizacion == null || !LocalDate.now().isAfter(venceAutorizacion);
    }

    /**
     * buscarRelacionResidente() : List&lt;Residente&gt; -> los residentes de TODOS sus inmuebles que lo tienen como
     * invitado, sin repetir.
     */
    public List<Residente> buscarRelacionResidente() {
        if (getInmuebles() == null) {
            return List.of();
        }
        return getInmuebles().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(inmueble -> inmueble.getUsuariosRelacionado().stream())
                .filter(u -> u instanceof Residente && !(u instanceof UsuarioAutorizado))
                .map(u -> (Residente) u)
                .filter(r -> r.tieneInvitado(this))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(Residente::getId, r -> r, (a, b) -> a,
                                java.util.LinkedHashMap::new),
                        m -> List.copyOf(m.values())));
    }
}
