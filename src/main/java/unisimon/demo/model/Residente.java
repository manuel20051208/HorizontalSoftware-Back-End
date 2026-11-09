package unisimon.demo.model;

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
 * Sus operaciones (verReservas, cambiarEstadoInvitado(s), consultarEstadoCuenta, consultarHistorialPago,
 * reservarZonaComun, cancelarReservaZonaComun, consultarNotificacion) estan en la capa de servicios.
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
}
