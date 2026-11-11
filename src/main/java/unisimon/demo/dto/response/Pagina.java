package unisimon.demo.dto.response;

import unisimon.demo.dto.request.SolicitudPagina;

import java.util.List;
import java.util.function.Function;

/**
 * Respuesta de todo listado paginado.
 *
 * @param contenido      filas de esta pagina, en el orden estable del listado
 * @param pagina         numero de pagina (empieza en 0)
 * @param tamano         filas pedidas por pagina
 * @param totalElementos filas que cumplen el filtro en todas las paginas
 * @param totalPaginas   paginas disponibles con ese tamano (0 si no hay filas)
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public static <T> Pagina<T> de(List<T> contenido, long totalElementos, SolicitudPagina solicitud) {
        int totalPaginas = (int) ((totalElementos + solicitud.tamano() - 1) / solicitud.tamano());
        return new Pagina<>(contenido, solicitud.pagina(), solicitud.tamano(), totalElementos, totalPaginas);
    }

    /** La misma pagina con cada fila transformada (p. ej. de proyeccion a DTO). */
    public <R> Pagina<R> map(Function<? super T, ? extends R> transformar) {
        List<R> filas = contenido.stream().<R>map(transformar).toList();
        return new Pagina<>(filas, pagina, tamano, totalElementos, totalPaginas);
    }
}
