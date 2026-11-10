package unisimon.demo.integration.wompi;

/**
 * Transaccion tal como la reporta la API REST de Wompi (fuente de verdad del resultado del pago).
 * estado: APPROVED, DECLINED, VOIDED, ERROR o PENDING.
 */
public record TransaccionWompi(String id, String referencia, String estado, long montoEnCentavos, String moneda) {

    public static final String APROBADA = "APPROVED";
    public static final String PENDIENTE = "PENDING";

    public boolean aprobada() {
        return APROBADA.equals(estado);
    }

    /** DECLINED, VOIDED y ERROR: el pago no se hizo. */
    public boolean fallida() {
        return "DECLINED".equals(estado) || "VOIDED".equals(estado) || "ERROR".equals(estado);
    }
}
