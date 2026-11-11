package unisimon.demo.config;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.exception.SolicitudInvalidaException;

/**
 * Resuelve el parametro {@link SolicitudPagina} de los controladores a partir de {@code ?pagina=&tamano=}.
 * Si faltan se usan los valores por defecto; si no son numeros enteros responde 400.
 */
public class SolicitudPaginaResolver implements HandlerMethodArgumentResolver {

    static final String PARAM_PAGINA = "pagina";
    static final String PARAM_TAMANO = "tamano";

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return SolicitudPagina.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                  NativeWebRequest peticion, WebDataBinderFactory fabrica) {
        int pagina = entero(peticion.getParameter(PARAM_PAGINA), PARAM_PAGINA, SolicitudPagina.PAGINA_POR_DEFECTO);
        int tamano = entero(peticion.getParameter(PARAM_TAMANO), PARAM_TAMANO, SolicitudPagina.TAMANO_POR_DEFECTO);
        return new SolicitudPagina(pagina, tamano);
    }

    static int entero(String valor, String nombre, int porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new SolicitudInvalidaException(nombre + " debe ser un numero entero");
        }
    }
}
