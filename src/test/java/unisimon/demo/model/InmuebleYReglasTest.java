package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InmuebleYReglasTest {

    @Test
    @DisplayName("Inmueble: pagoAlDiaAdministracion solo con liquidacion AL_DIA del propio inmueble")
    void pagoAlDia() {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, true);
        Liquidacion l = new Liquidacion(1L, inmueble, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(inmueble.pagoAlDiaAdministracion(l)).isFalse();
        l.marcarAlDia();
        assertThat(inmueble.pagoAlDiaAdministracion(l)).isTrue();
        assertThat(inmueble.pagoAlDiaAdministracion(null)).isFalse();

        Liquidacion ajena = new Liquidacion(2L, new Inmueble(2L, "B-1", null, true), LocalDate.of(2026, 11, 1),
                null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        ajena.marcarAlDia();
        assertThat(inmueble.pagoAlDiaAdministracion(ajena)).isFalse();
    }

    @Test
    @DisplayName("Inmueble: mostrarUsuarios devuelve nombres (los de la lista dada o los suyos)")
    void mostrarUsuarios() {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, true);
        Residente r = new Residente();
        r.setNombreCompleto("Santiago");
        inmueble.getUsuariosRelacionado().add(r);
        Usuario otro = new Usuario();
        otro.setNombreCompleto("Otro");

        assertThat(inmueble.mostrarUsuarios(null)).containsExactly("Santiago");
        assertThat(inmueble.mostrarUsuarios(List.of(otro))).containsExactly("Otro");
    }

    @Test
    @DisplayName("Inmueble: el parqueadero queda enlazado y isActive es true por defecto")
    void constructor() {
        Parqueadero p = new Parqueadero(1L, null, new BigDecimal("120000"));
        Inmueble inmueble = new Inmueble(1L, "A-302", p, null);

        assertThat(p.getInmueble()).isSameAs(inmueble);
        assertThat(inmueble.getIsActive()).isTrue();
        assertThat(p.obtenerValorMensual()).isEqualByComparingTo("120000");
        assertThat(new Parqueadero(2L, null, null).obtenerValorMensual()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ReglaReserva.actualizarReglas valida los valores nuevos")
    void actualizarReglas() {
        ReglaReserva regla = new ReglaReserva(1L, 24, 4, 12, new BigDecimal("30000"), null);

        regla.actualizarReglas(48, 6);
        assertThat(regla.getAnticipacionMinimaHoras()).isEqualTo(48);
        assertThat(regla.getDuracionMaximaHoras()).isEqualTo(6);

        assertThatThrownBy(() -> regla.actualizarReglas(-1, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regla.actualizarReglas(10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(regla.getAnticipacionMinimaHoras()).isEqualTo(48);
    }

    @Test
    @DisplayName("Rompimiento: aplicarRegla activa y fecha hoy; inactivarRegla la apaga")
    void rompimiento() {
        Rompimiento r = new Rompimiento(1L, new ZonaComun(), new ReglaReserva(), LocalDate.of(2020, 1, 1), "obs");

        r.inactivarRegla();
        assertThat(r.getActiva()).isFalse();

        r.aplicarRegla();
        assertThat(r.getActiva()).isTrue();
        assertThat(r.getFechaAplicacion()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Residente.tieneInvitado compara por id")
    void tieneInvitado() {
        Residente residente = new Residente();
        UsuarioAutorizado invitado = new UsuarioAutorizado();
        invitado.setId(5L);
        residente.getInvitados().add(invitado);

        UsuarioAutorizado mismoId = new UsuarioAutorizado();
        mismoId.setId(5L);
        UsuarioAutorizado otro = new UsuarioAutorizado();
        otro.setId(6L);

        assertThat(residente.tieneInvitado(mismoId)).isTrue();
        assertThat(residente.tieneInvitado(otro)).isFalse();
        assertThat(residente.tieneInvitado(null)).isFalse();
    }
}
