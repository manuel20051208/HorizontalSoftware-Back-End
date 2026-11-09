package unisimon.demo.model;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pasarela de pagos (Wompi). {@code urlApi} es la URL del checkout y {@code apiKey} la llave publica del comercio.
 * Estados: ACTIVA / INACTIVA.
 * <p>
 * confirmarTransaccion, consultarEstado y notificarResultado necesitan base de datos y estan en la capa de servicios.
 */
@Entity
@DynamicUpdate
@Table(name = "pasarelas_pago")
@Getter
@Setter
@NoArgsConstructor
public class PasarelaPago {

    public static final String ACTIVA = "ACTIVA";
    public static final String INACTIVA = "INACTIVA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombrePasarela;

    @Column(nullable = false)
    private String urlApi;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String estado = ACTIVA;

    public PasarelaPago(Long id, String nombrePasarela, String urlApi, String apiKey) {
        this.id = id;
        this.nombrePasarela = nombrePasarela;
        this.urlApi = urlApi;
        this.apiKey = apiKey;
        this.estado = ACTIVA;
    }

    /**
     * iniciarTransaccion(pago : Pago) : String.
     * Asigna la referencia al pago (si no la tiene), lo deja asociado a esta pasarela y devuelve la URL
     * del checkout a la que debe ir el residente.
     */
    public String iniciarTransaccion(Pago pago) {
        if (!ACTIVA.equals(estado)) {
            throw new IllegalStateException("La pasarela " + nombrePasarela + " esta inactiva");
        }
        if (pago == null || pago.getId() == null) {
            throw new IllegalArgumentException("El pago debe estar registrado antes de iniciar la transaccion");
        }
        if (!Pago.PENDIENTE.equals(pago.getEstado())) {
            throw new IllegalStateException("Solo se puede iniciar la transaccion de un pago PENDIENTE");
        }
        if (pago.getReferencia() == null || pago.getReferencia().isBlank()) {
            pago.setReferencia("PAGO-" + pago.getId() + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        pago.setPasarela(this);
        long centavos = pago.getMonto().movePointRight(2).longValueExact();
        String separador = urlApi.contains("?") ? "&" : "?";
        return urlApi + separador
                + "public-key=" + enc(apiKey)
                + "&currency=COP"
                + "&amount-in-cents=" + centavos
                + "&reference=" + enc(pago.getReferencia());
    }

    /** reembolsar(pago : Pago) : Boolean -> solo se reembolsa un pago aprobado. */
    public Boolean reembolsar(Pago pago) {
        if (pago == null || !Boolean.TRUE.equals(pago.estaAprobado())) {
            return false;
        }
        pago.setEstado(Pago.REEMBOLSADO);
        return true;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
