package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Inmueble (apartamento). {@code direccionInmueble} se maneja con el formato "TORRE-APTO", por ejemplo "A-302".
 * <p>
 * Notas de mapeo respecto al diagrama:
 * <ul>
 *   <li>{@code usuariosRelacionado} son los residentes (e invitados) cuyo {@code inmueble} es este.</li>
 *   <li>{@code liquidacion} es la liquidacion VIGENTE (la mas reciente); el historial esta en {@link Liquidacion}.</li>
 *   <li>{@code zonaComunes} es muchos a muchos: las zonas son compartidas por varios inmuebles.</li>
 * </ul>
 */
@Entity
@DynamicUpdate
@Table(name = "inmuebles")
@Getter
@Setter
@NoArgsConstructor
public class Inmueble {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "inmueble")
    private List<Residente> usuariosRelacionado = new ArrayList<>();

    @Column(nullable = false, unique = true)
    private String direccionInmueble;

    @OneToOne(mappedBy = "inmueble", cascade = CascadeType.ALL, orphanRemoval = true)
    private Parqueadero parqueadero;

    @ManyToMany
    @JoinTable(name = "inmueble_zonas_comunes",
            joinColumns = @JoinColumn(name = "inmueble_id"),
            inverseJoinColumns = @JoinColumn(name = "zona_comun_id"))
    private List<ZonaComun> zonaComunes = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "liquidacion_actual_id")
    private Liquidacion liquidacion;

    @OneToMany(mappedBy = "inmueble")
    private List<Pago> registroPagos = new ArrayList<>();

    @Column(nullable = false)
    private Boolean isActive = true;

    @ManyToMany
    @JoinTable(name = "inmueble_notificaciones",
            joinColumns = @JoinColumn(name = "inmueble_id"),
            inverseJoinColumns = @JoinColumn(name = "notificacion_id"))
    private List<Notificacion> notificaciones = new ArrayList<>();

    public Inmueble(Long id, String direccionInmueble, Parqueadero parqueadero, Boolean isActive) {
        this.id = id;
        this.direccionInmueble = direccionInmueble;
        this.parqueadero = parqueadero;
        if (parqueadero != null) {
            parqueadero.setInmueble(this);
        }
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }

    /** pagoAlDiaAdministracion(liquidacion : Liquidacion) : boolean */
    public boolean pagoAlDiaAdministracion(Liquidacion liquidacion) {
        return liquidacion != null
                && (liquidacion.getInmueble() == null || this.equals(liquidacion.getInmueble()))
                && Liquidacion.AL_DIA.equals(liquidacion.getEstado());
    }

    /** mostrarUsuarios(List&lt;Usuario&gt;) : List&lt;String&gt; -> nombres; sin argumento usa usuariosRelacionado. */
    public List<String> mostrarUsuarios(List<? extends Usuario> usuarios) {
        List<? extends Usuario> origen = usuarios != null ? usuarios : usuariosRelacionado;
        return origen.stream().map(Usuario::getNombreCompleto).toList();
    }

    /** Residentes titulares y activos (excluye invitados): son quienes reciben las notificaciones del inmueble. */
    public List<Usuario> residentesActivos() {
        return usuariosRelacionado.stream()
                .filter(r -> !(r instanceof UsuarioAutorizado) && r.isActive())
                .map(r -> (Usuario) r)
                .toList();
    }
}
