package unisimon.demo.model;

import lombok.AccessLevel;
import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.Transient;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pasarela de pagos (Wompi). {@code urlApi} es la URL del checkout y {@code apiKey} la llave publica del comercio.
 * Estados: ACTIVA / INACTIVA.
 * <p>
 * confirmarTransaccion, consultarEstado y notificarResultado trabajan sobre los pagos que el servicio deja en
 * {@code pagos}; preguntarle a Wompi y guardar en la base de datos lo hace la capa de servicios.
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

    /**
     * Contexto (no es atributo del diagrama ni se guarda): el secreto de integridad con el que se firma el
     * checkout. Viene de la configuracion, no tiene getter y nunca toca la base de datos ni las respuestas.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private String secretoIntegridad;

    /** Contexto (no es atributo del diagrama ni se guarda): los pagos procesados por esta pasarela. */
    @Transient
    private List<Pago> pagos = new ArrayList<>();

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
     * del checkout firmada (signature:integrity) a la que debe ir el residente.
     * <p>
     * Firma con el {@code secretoIntegridad} que el servicio dejo en la pasarela; de aqui solo sale su hash,
     * que no permite recuperarlo.
     */
    public String iniciarTransaccion(Pago pago) {
        return iniciarTransaccion(pago, secretoIntegridad, null);
    }

    /** Variante con el secreto por parametro (no se guarda en la entidad). */
    public String iniciarTransaccion(Pago pago, String secretoIntegridad) {
        return iniciarTransaccion(pago, secretoIntegridad, null);
    }

    /** Igual que {@link #iniciarTransaccion(Pago, String)}; {@code redirectUrl} (opcional) es a donde vuelve el residente. */
    public String iniciarTransaccion(Pago pago, String secretoIntegridad, String redirectUrl) {
        if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
            throw new IllegalStateException("El secreto de integridad de la pasarela no esta configurado");
        }
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
                + "&currency=" + MONEDA
                + "&amount-in-cents=" + centavos
                + "&reference=" + enc(pago.getReferencia())
                + "&signature:integrity=" + firmaIntegridad(pago.getReferencia(), centavos, secretoIntegridad)
                + (redirectUrl == null || redirectUrl.isBlank() ? "" : "&redirect-url=" + enc(redirectUrl.trim()));
    }

    private static final String MONEDA = "COP";

    /** Firma de integridad de Wompi: SHA-256 (hex) de referencia + centavos + moneda + secreto. */
    static String firmaIntegridad(String referencia, long centavos, String secretoIntegridad) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (referencia + centavos + MONEDA + secretoIntegridad).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * confirmarTransaccion(referencia : String) : Boolean -> la transaccion con esa referencia quedo aprobada.
     */
    public Boolean confirmarTransaccion(String referencia) {
        Pago pago = buscarPago(referencia);
        return pago != null && pago.estaAprobado(pago);
    }

    /** consultarEstado(referencia : String) : String -> estado del pago con esa referencia; null si no existe. */
    public String consultarEstado(String referencia) {
        Pago pago = buscarPago(referencia);
        return pago == null ? null : pago.getEstado();
    }

    /**
     * notificarResultado(pago : Pago): avisa el resultado del pago a los residentes activos de su inmueble
     * (Notificacion.notificarActualizacionDePago) y deja la notificacion en el inmueble.
     */
    public void notificarResultado(Pago pago) {
        if (pago == null || pago.getInmueble() == null) {
            throw new IllegalArgumentException("El pago debe ser de un inmueble");
        }
        Inmueble inmueble = pago.getInmueble();
        Notificacion notificacion = new Notificacion();
        notificacion.notificarActualizacionDePago(inmueble, inmueble.residentesActivos());
        if (notificacion.getFechaEnvio() != null) {
            inmueble.getNotificaciones().add(notificacion);
        }
    }

    private Pago buscarPago(String referencia) {
        return referencia == null ? null : pagos.stream()
                .filter(p -> referencia.equals(p.getReferencia()))
                .findFirst().orElse(null);
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
