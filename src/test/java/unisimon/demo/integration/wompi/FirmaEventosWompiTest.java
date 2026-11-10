package unisimon.demo.integration.wompi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FirmaEventosWompiTest {

    public static final String SECRETO = "test_events_abc";
    /** printf '%s' 1234-1610641025-49201 APPROVED 50500000 1530291411 test_events_abc | sha256sum */
    public static final String CHECKSUM = "efdf5922c14f0ee5507b745386f088175ee780907e496da8eacd958706c9c89a";

    /** Evento con la forma que envia Wompi (los numeros llegan como Integer/Long desde Jackson). */
    public static Map<String, Object> evento(String status, Object monto, String checksum) {
        Map<String, Object> tx = new HashMap<>();
        tx.put("id", "1234-1610641025-49201");
        tx.put("status", status);
        tx.put("amount_in_cents", monto);
        tx.put("reference", "PAGO-10");
        Map<String, Object> evento = new HashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", Map.of("transaction", tx));
        evento.put("signature", Map.of("properties",
                List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"), "checksum", checksum));
        evento.put("timestamp", 1530291411);
        return evento;
    }

    @Test
    @DisplayName("acepta el evento con checksum correcto (tambien en mayusculas)")
    void valida() {
        assertThat(FirmaEventosWompi.esValida(evento("APPROVED", 50500000, CHECKSUM), SECRETO)).isTrue();
        assertThat(FirmaEventosWompi.esValida(evento("APPROVED", 50500000L, CHECKSUM.toUpperCase()), SECRETO)).isTrue();
    }

    @Test
    @DisplayName("rechaza eventos alterados, con otro secreto o sin firma")
    void invalida() {
        // Alguien cambia el estado o el monto sin conocer el secreto: el checksum ya no cuadra.
        assertThat(FirmaEventosWompi.esValida(evento("DECLINED", 50500000, CHECKSUM), SECRETO)).isFalse();
        assertThat(FirmaEventosWompi.esValida(evento("APPROVED", 100, CHECKSUM), SECRETO)).isFalse();
        assertThat(FirmaEventosWompi.esValida(evento("APPROVED", 50500000, CHECKSUM), "otro_secreto")).isFalse();
        assertThat(FirmaEventosWompi.esValida(evento("APPROVED", 50500000, CHECKSUM), "")).isFalse();

        Map<String, Object> sinFirma = evento("APPROVED", 50500000, CHECKSUM);
        sinFirma.remove("signature");
        assertThat(FirmaEventosWompi.esValida(sinFirma, SECRETO)).isFalse();

        Map<String, Object> sinTimestamp = evento("APPROVED", 50500000, CHECKSUM);
        sinTimestamp.remove("timestamp");
        assertThat(FirmaEventosWompi.esValida(sinTimestamp, SECRETO)).isFalse();

        assertThat(FirmaEventosWompi.esValida(Map.of(), SECRETO)).isFalse();
        assertThat(FirmaEventosWompi.esValida(null, SECRETO)).isFalse();
    }

    @Test
    @DisplayName("valor lee rutas con puntos dentro de mapas anidados")
    void valor() {
        Map<String, Object> e = evento("APPROVED", 1, CHECKSUM);
        assertThat(FirmaEventosWompi.valor(e, "data.transaction.reference")).isEqualTo("PAGO-10");
        assertThat(FirmaEventosWompi.valor(e, "data.no.existe")).isNull();
    }
}
