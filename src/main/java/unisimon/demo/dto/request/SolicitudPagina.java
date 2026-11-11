package unisimon.demo.dto.request;

import unisimon.demo.exception.SolicitudInvalidaException;

/**
 * Pagina pedida por el cliente en los listados: {@code ?pagina=0&tamano=20}.
 * <p>
 * {@code pagina} empieza en 0. {@code tamano} va de 1 a {@value #TAMANO_MAXIMO}; si no se envia se usa
 * {@value #TAMANO_POR_DEFECTO}. Un valor fuera de rango responde 400 en vez de recortarse en silencio.
 */
public record SolicitudPagina(int pagina, int tamano) {

    public static final int PAGINA_POR_DEFECTO = 0;
    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    public SolicitudPagina {
        if (pagina < 0) {
            throw new SolicitudInvalidaException("pagina no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException("tamano debe estar entre 1 y " + TAMANO_MAXIMO);
        }
    }

    /** Primera pagina con el tamano por defecto. */
    public static SolicitudPagina primera() {
        return new SolicitudPagina(PAGINA_POR_DEFECTO, TAMANO_POR_DEFECTO);
    }

    /** Filas por pagina: valor del {@code LIMIT} de la consulta. */
    public int limite() {
        return tamano;
    }

    /** Filas que se saltan: valor del {@code OFFSET} de la consulta. */
    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
