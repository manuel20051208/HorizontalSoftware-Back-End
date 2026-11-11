package unisimon.demo.repository;

/**
 * Fragmento SQL comun de los listados paginados. Cada consulta paginada termina con un {@code ORDER BY} estable
 * (con desempate por id) para que una fila no salte ni se repita entre paginas, y recibe {@code :limite} y
 * {@code :desplazamiento} de {@link unisimon.demo.dto.request.SolicitudPagina}.
 */
public final class SqlPaginacion {

    public static final String LIMITE_DESPLAZAMIENTO = "\nLIMIT :limite OFFSET :desplazamiento\n";

    private SqlPaginacion() {
    }
}
