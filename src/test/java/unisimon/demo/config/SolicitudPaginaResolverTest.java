package unisimon.demo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.exception.SolicitudInvalidaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitudPaginaResolverTest {

    private final SolicitudPaginaResolver resolver = new SolicitudPaginaResolver();

    private Object resolver(String pagina, String tamano) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        if (pagina != null) {
            peticion.setParameter("pagina", pagina);
        }
        if (tamano != null) {
            peticion.setParameter("tamano", tamano);
        }
        return resolver.resolveArgument(null, null, new ServletWebRequest(peticion), null);
    }

    @Test
    @DisplayName("sin parametros entrega la primera pagina por defecto")
    void porDefecto() {
        assertThat(resolver(null, null)).isEqualTo(SolicitudPagina.primera());
        assertThat(resolver(" ", "")).isEqualTo(SolicitudPagina.primera());
    }

    @Test
    @DisplayName("lee pagina y tamano de la query")
    void leeParametros() {
        assertThat(resolver("2", " 50 ")).isEqualTo(new SolicitudPagina(2, 50));
    }

    @Test
    @DisplayName("un valor que no es entero o esta fuera de rango responde 400")
    void invalidos() {
        assertThatThrownBy(() -> resolver("uno", null)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> resolver(null, "1.5")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> resolver("0", "500")).isInstanceOf(SolicitudInvalidaException.class);
    }
}
