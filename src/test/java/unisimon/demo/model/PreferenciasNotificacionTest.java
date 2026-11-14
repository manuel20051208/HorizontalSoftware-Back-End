package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Usuario.preferenciasNotificacion y Usuario.configurarPreferencias(tipoNotificacion). */
class PreferenciasNotificacionTest {

    private static Usuario usuario(boolean activo) {
        Usuario u = new Usuario(1L, 100L, "Ana", null, "ana@mail.com", "hash", Usuario.RESIDENTE);
        u.setActive(activo);
        return u;
    }

    @Test
    @DisplayName("una cuenta nueva no tiene preferencias y por eso recibe todos los tipos")
    void sinPreferencias() {
        Usuario u = usuario(true);

        assertThat(u.getPreferenciasNotificacion()).isEmpty();
        assertThat(u.recibe("RESERVA")).isTrue();
        assertThat(u.recibe("VISITA")).isTrue();
    }

    @Test
    @DisplayName("configurarPreferencias agrega el tipo (normalizado) y desde ahi solo recibe los elegidos")
    void agrega() {
        Usuario u = usuario(true);

        u.configurarPreferencias(" reserva ");
        u.configurarPreferencias("PAGO_PENDIENTE");

        assertThat(u.getPreferenciasNotificacion()).containsExactly("RESERVA", "PAGO_PENDIENTE");
        assertThat(u.recibe("RESERVA")).isTrue();
        assertThat(u.recibe("VISITA")).isFalse();
    }

    @Test
    @DisplayName("configurarPreferencias con un tipo que ya estaba lo quita")
    void quita() {
        Usuario u = usuario(true);
        u.configurarPreferencias("RESERVA");

        u.configurarPreferencias("RESERVA");

        assertThat(u.getPreferenciasNotificacion()).isEmpty();
    }

    @Test
    @DisplayName("una cuenta inactiva no puede configurar preferencias y el tipo es obligatorio")
    void rechazos() {
        assertThatThrownBy(() -> usuario(false).configurarPreferencias("RESERVA"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> usuario(true).configurarPreferencias(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
