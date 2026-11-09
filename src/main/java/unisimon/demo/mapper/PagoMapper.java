package unisimon.demo.mapper;

import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;

/** Proyecciones de pagos y pasarela -> respuestas del API. */
public final class PagoMapper {

    private static final int CARACTERES_VISIBLES = 4;

    private PagoMapper() {
    }

    public static PagoResponses.Pago de(PagoView v) {
        return new PagoResponses.Pago(v.getId(), v.getInmuebleId(), v.getDireccionInmueble(), v.getMonto(),
                v.getFechaPago(), v.getConcepto(), v.getMetodoPago(), v.getEstado(), v.getReferencia());
    }

    public static PagoResponses.Pasarela pasarela(PasarelaView v) {
        return new PagoResponses.Pasarela(v.getId(), v.getNombrePasarela(), v.getUrlApi(), enmascarar(v.getApiKey()),
                v.getEstado());
    }

    /** Deja solo los ultimos caracteres de una llave: "********abcd". */
    static String enmascarar(String llave) {
        if (llave == null || llave.length() <= CARACTERES_VISIBLES) {
            return "****";
        }
        return "*".repeat(llave.length() - CARACTERES_VISIBLES) + llave.substring(llave.length() - CARACTERES_VISIBLES);
    }
}
