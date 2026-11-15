package unisimon.demo.model;

import java.util.List;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Auto de un residente. {@code placa} es Long porque asi esta en el diagrama
 * (las placas colombianas llevan letras: conviene cambiarla a String en el diagrama).
 * consultarDuenos(inmueble, id) recorre los residentes del inmueble, que el servicio deja cargados.
 */
@Entity
@DynamicUpdate
@Table(name = "autos")
@Getter
@Setter
@NoArgsConstructor
public class Auto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dueno_id", unique = true)
    private Residente dueno;

    @Column(nullable = false, unique = true)
    private Long placa;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parqueadero_id", unique = true)
    private Paqueadero parqueadero;

    public Auto(Long id, Residente dueno, Long placa, Paqueadero parqueadero) {
        this.id = id;
        this.dueno = dueno;
        this.placa = placa;
        this.parqueadero = parqueadero;
    }

    /**
     * consultarDuenos(inmueble : Inmueble, id : Long) : List&lt;Residente&gt;.
     * Los duenos del auto {@code id} (este) en ese inmueble: su dueno registrado y los demas residentes
     * titulares del inmueble, que comparten el vehiculo. Vacio si el auto no es de ese inmueble.
     */
    public List<Residente> consultarDuenos(Inmueble inmueble, Long id) {
        if (inmueble == null || id == null || !id.equals(this.id) || dueno == null) {
            return List.of();
        }
        List<Residente> titulares = inmueble.getUsuariosRelacionado().stream()
                .filter(u -> u instanceof Residente && !(u instanceof UsuarioAutorizado))
                .map(u -> (Residente) u)
                .toList();
        boolean esDelInmueble = titulares.stream()
                .anyMatch(r -> r == dueno || (r.getId() != null && r.getId().equals(dueno.getId())));
        return esDelInmueble ? titulares : List.of();
    }
}
