package unisimon.demo.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.SolicitudInvalidaException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacionTest {

    @Test
    @DisplayName("la solicitud traduce pagina y tamano a LIMIT y OFFSET")
    void limiteYDesplazamiento() {
        SolicitudPagina solicitud = new SolicitudPagina(3, 25);

        assertThat(solicitud.limite()).isEqualTo(25);
        assertThat(solicitud.desplazamiento()).isEqualTo(75L);
    }

    @Test
    @DisplayName("la primera pagina por defecto es la 0 con 20 filas")
    void porDefecto() {
        assertThat(SolicitudPagina.primera()).isEqualTo(new SolicitudPagina(0, 20));
    }

    @Test
    @DisplayName("una pagina negativa o un tamano fuera de 1..100 responde 400")
    void fueraDeRango() {
        assertThatThrownBy(() -> new SolicitudPagina(-1, 20)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> new SolicitudPagina(0, 0)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> new SolicitudPagina(0, 101)).isInstanceOf(SolicitudInvalidaException.class);
        assertThat(new SolicitudPagina(0, 100).tamano()).isEqualTo(100);
    }

    @Test
    @DisplayName("el desplazamiento no se desborda en paginas muy altas")
    void sinDesbordamiento() {
        assertThat(new SolicitudPagina(Integer.MAX_VALUE, 100).desplazamiento())
                .isEqualTo((long) Integer.MAX_VALUE * 100);
    }

    @Test
    @DisplayName("el total de paginas redondea hacia arriba y es 0 sin filas")
    void totalPaginas() {
        SolicitudPagina solicitud = new SolicitudPagina(0, 20);

        assertThat(Pagina.de(List.of(), 0, solicitud).totalPaginas()).isZero();
        assertThat(Pagina.de(List.of(), 20, solicitud).totalPaginas()).isEqualTo(1);
        assertThat(Pagina.de(List.of(), 21, solicitud).totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("map transforma las filas y conserva los datos de la pagina")
    void map() {
        Pagina<Integer> numeros = Pagina.de(List.of(1, 2), 42, new SolicitudPagina(1, 2));

        Pagina<String> textos = numeros.map(n -> "#" + n);

        assertThat(textos.contenido()).containsExactly("#1", "#2");
        assertThat(textos.pagina()).isEqualTo(1);
        assertThat(textos.tamano()).isEqualTo(2);
        assertThat(textos.totalElementos()).isEqualTo(42L);
        assertThat(textos.totalPaginas()).isEqualTo(21);
    }
}
