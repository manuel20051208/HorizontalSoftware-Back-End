package unisimon.demo.dto.response;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Respuestas sobre parqueaderos. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParqueaderoResponses {

    public record Parqueadero(Long id, Long inmuebleId, BigDecimal valorMensual) {
    }
}
