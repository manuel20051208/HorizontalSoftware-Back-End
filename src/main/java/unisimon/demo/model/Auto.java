package unisimon.demo.model;

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
 * Operacion del diagrama: consultarDuenos(inmueble, id) -> capa de servicios.
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
    private Parqueadero parqueadero;

    public Auto(Long id, Residente dueno, Long placa, Parqueadero parqueadero) {
        this.id = id;
        this.dueno = dueno;
        this.placa = placa;
        this.parqueadero = parqueadero;
    }
}
