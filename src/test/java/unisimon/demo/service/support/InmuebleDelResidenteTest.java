package unisimon.demo.service.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.support.Proyecciones;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** El selector centralizado: con varios inmuebles exige ?inmuebleId, ajeno es 403 y sin inmuebles 409. */
@ExtendWith(MockitoExtension.class)
class InmuebleDelResidenteTest {

    @Mock
    private UsuarioRepository usuarios;

    private InmuebleDelResidente selector;

    @BeforeEach
    void preparar() {
        selector = new InmuebleDelResidente(usuarios);
    }

    private InmuebleBasicoView inmueble(Long id, String direccion) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", id, "direccionInmueble", direccion, "isActive", true);
    }

    @Test
    @DisplayName("con un solo inmueble se usa ese aunque no venga el selector")
    void unSoloInmueble() {
        when(usuarios.listarInmueblesDe(5L)).thenReturn(List.of(inmueble(2L, "B-201")));

        assertThat(selector.resolver(5L, null).getId()).isEqualTo(2L);
        assertThat(selector.resolver(5L, 2L).getDireccionInmueble()).isEqualTo("B-201");
        assertThat(selector.listar(5L)).hasSize(1);
    }

    @Test
    @DisplayName("con varios inmuebles y sin selector responde 400")
    void variosSinSelector() {
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(2L, "B-201"), inmueble(3L, "C-101")));

        assertThatThrownBy(() -> selector.resolver(5L, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("Indique el inmueble");
    }

    @Test
    @DisplayName("con varios inmuebles, el elegido entre ellos es el que se usa")
    void variosConSelector() {
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(2L, "B-201"), inmueble(3L, "C-101")));

        assertThat(selector.resolver(5L, 3L).getDireccionInmueble()).isEqualTo("C-101");
    }

    @Test
    @DisplayName("un inmueble ajeno responde 403")
    void inmuebleAjeno() {
        when(usuarios.listarInmueblesDe(5L)).thenReturn(List.of(inmueble(2L, "B-201")));

        assertThatThrownBy(() -> selector.resolver(5L, 9L))
                .isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("no es suyo");
    }

    @Test
    @DisplayName("sin ningun inmueble responde 409")
    void sinInmuebles() {
        when(usuarios.listarInmueblesDe(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> selector.resolver(5L, null))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Primero relacione la cuenta con un inmueble");
    }
}
