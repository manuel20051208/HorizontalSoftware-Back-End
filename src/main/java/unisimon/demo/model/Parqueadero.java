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

import java.math.BigDecimal;

/** Parqueadero (en el diagrama aparece como "Paqueadero"). Pertenece a un unico inmueble. */
@Entity
@DynamicUpdate
@Table(name = "parqueaderos")
@Getter
@Setter
@NoArgsConstructor
public class Parqueadero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id", unique = true)
    private Inmueble inmueble;

    @Column(nullable = false)
    private BigDecimal valorMensual = BigDecimal.ZERO;

    public Parqueadero(Long id, Inmueble inmueble, BigDecimal valorMensual) {
        this.id = id;
        this.inmueble = inmueble;
        this.valorMensual = valorMensual == null ? BigDecimal.ZERO : valorMensual;
    }

    /** obtenerValorMensual() : BigDecimal */
    public BigDecimal obtenerValorMensual() {
        return valorMensual == null ? BigDecimal.ZERO : valorMensual;
    }
}
