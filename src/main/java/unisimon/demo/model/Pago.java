package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Pago de la administracion de un inmueble, procesado por una {@link PasarelaPago}.
 * Estados: PENDIENTE, APROBADO, RECHAZADO, ANULADO y REEMBOLSADO.
 * <p>
 * registrarPago(inmueble) y la parte de actualizarEstado(pago) que recorre liquidaciones estan en la capa de servicios.
 */
@Entity
@DynamicUpdate
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ANULADO = "ANULADO";
    public static final String REEMBOLSADO = "REEMBOLSADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id")
    private Inmueble inmueble;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pasarela_id")
    private PasarelaPago pasarela;

    @Column(nullable = false)
    private BigDecimal monto;

    private LocalDate fechaPago;

    private String concepto;

    private String metodoPago;

    @Column(nullable = false)
    private String estado = PENDIENTE;

    @Column(unique = true)
    private String referencia;

    public Pago(Long id, Inmueble inmueble, PasarelaPago pasarela, BigDecimal monto, LocalDate fechaPago,
                String concepto, String metodoPago, String referencia) {
        this.id = id;
        this.inmueble = inmueble;
        this.pasarela = pasarela;
        this.monto = monto;
        this.fechaPago = fechaPago;
        this.concepto = concepto;
        this.metodoPago = metodoPago;
        this.referencia = referencia;
        this.estado = PENDIENTE;
    }

    /** validarMonto(liquidacion : Liquidacion) : Boolean -> el monto es exactamente el total de la liquidacion. */
    public Boolean validarMonto(Liquidacion liquidacion) {
        return liquidacion != null && validarMonto(liquidacion.getTotal());
    }

    /** Variante para saldos que suman varias liquidaciones. */
    public Boolean validarMonto(BigDecimal esperado) {
        return monto != null && esperado != null && monto.signum() > 0 && monto.compareTo(esperado) == 0;
    }

    /** estaAprobado(pago : Pago) : Boolean */
    public Boolean estaAprobado(Pago pago) {
        return pago != null && APROBADO.equals(pago.getEstado());
    }

    public Boolean estaAprobado() {
        return estaAprobado(this);
    }

    /** anularPago(): solo se anula un pago PENDIENTE (uno aprobado se reembolsa). */
    public void anularPago() {
        if (!PENDIENTE.equals(estado)) {
            throw new IllegalStateException("Solo se puede anular un pago PENDIENTE; este esta " + estado);
        }
        this.estado = ANULADO;
    }

    /**
     * actualizarEstado(pago : Pago) : Liquidacion.
     * Si el pago esta aprobado deja AL_DIA la liquidacion vigente de su inmueble y la devuelve.
     * (PagoService hace lo mismo con las liquidaciones anteriores que sigan sin pagar.)
     */
    public Liquidacion actualizarEstado(Pago pago) {
        Liquidacion vigente = pago.getInmueble().getLiquidacion();
        if (vigente != null && estaAprobado(pago)) {
            vigente.marcarAlDia();
        }
        return vigente;
    }

    /** generarComprobante() : String -> solo para pagos aprobados. */
    public String generarComprobante() {
        if (!APROBADO.equals(estado) && !REEMBOLSADO.equals(estado)) {
            throw new IllegalStateException("Solo los pagos aprobados tienen comprobante");
        }
        NumberFormat pesos = NumberFormat.getIntegerInstance(Locale.of("es", "CO"));
        return String.join("\n",
                "COMPROBANTE DE PAGO - TORRES DEL PARQUE",
                "Referencia: " + referencia,
                "Inmueble:   " + inmueble.getDireccionInmueble(),
                "Concepto:   " + concepto,
                "Valor:      $ " + pesos.format(monto),
                "Metodo:     " + metodoPago,
                "Fecha:      " + fechaPago,
                "Estado:     " + estado);
    }
}
