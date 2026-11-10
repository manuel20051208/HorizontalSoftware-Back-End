package unisimon.demo.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler();

    @Test
    @DisplayName("una ruta inexistente (GET /) responde 404 y no 500")
    void rutaInexistente() {
        var respuesta = manejador.rutaNoEncontrada(new NoResourceFoundException(HttpMethod.GET, "/", ""));
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("un metodo HTTP no soportado responde 405 y no 500")
    void metodoNoPermitido() {
        var respuesta = manejador.metodoNoPermitido(new HttpRequestMethodNotSupportedException("DELETE"));
        assertThat(respuesta.getStatusCode().value()).isEqualTo(405);
    }
}
