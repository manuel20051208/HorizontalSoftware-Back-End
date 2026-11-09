package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Los constructores de los hijos reciben los atributos de su padre y los pasan a Usuario. */
class HerenciaUsuarioTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(1990, 5, 10);

    @Test
    @DisplayName("Administrador recibe los atributos de Usuario y los propios; no es NoAdmin")
    void administrador() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        Administrador a = new Administrador(1L, 100L, "Ana", NACIMIENTO, "ana@mail.com", "hash",
                Usuario.ADMINISTRADOR, inicio, inicio.plusYears(1));

        assertThat(a.getId()).isEqualTo(1L);
        assertThat(a.getIdentificacion()).isEqualTo(100L);
        assertThat(a.getNombreCompleto()).isEqualTo("Ana");
        assertThat(a.getFechaNacimiento()).isEqualTo(NACIMIENTO);
        assertThat(a.getCorreoElectronico()).isEqualTo("ana@mail.com");
        assertThat(a.getContrasena()).isEqualTo("hash");
        assertThat(a.getTipoUsuario()).isEqualTo(Usuario.ADMINISTRADOR);
        assertThat(a.getInicioContrato()).isEqualTo(inicio);
        assertThat(a).isNotInstanceOf(NoAdmin.class);
        assertThat(a.contratoVigente()).isEqualTo(
                !LocalDate.now().isBefore(inicio) && !LocalDate.now().isAfter(inicio.plusYears(1)));
        a.setVenceContrato(LocalDate.now().minusDays(1));
        assertThat(a.contratoVigente()).isFalse();
    }

    @Test
    @DisplayName("Vigilante e implementa NoAdmin y guarda su contrato")
    void vigilante() {
        Vigilante v = new Vigilante(2L, 200L, "Hernando", NACIMIENTO, "h@mail.com", "hash", Usuario.VIGILANTE,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));

        assertThat(v).isInstanceOf(NoAdmin.class);
        assertThat(v.getNombreCompleto()).isEqualTo("Hernando");
        assertThat(v.contratoVigente()).isTrue();
    }

    @Test
    @DisplayName("Residente recibe los atributos de Usuario, su inmueble y su auto")
    void residente() {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, true);
        Auto auto = new Auto(1L, null, 123456L, null);
        Residente r = new Residente(3L, 300L, "Santiago", NACIMIENTO, "s@mail.com", "hash", Usuario.RESIDENTE,
                inmueble, auto);

        assertThat(r).isInstanceOf(NoAdmin.class).isInstanceOf(Usuario.class);
        assertThat(r.getIdentificacion()).isEqualTo(300L);
        assertThat(r.getInmueble()).isSameAs(inmueble);
        assertThat(r.getAuto()).isSameAs(auto);
        assertThat(r.getInvitados()).isEmpty();
    }

    @Test
    @DisplayName("UsuarioAutorizado hereda de Residente (y de Usuario): arrastra inmueble y auto")
    void usuarioAutorizado() {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, true);
        LocalDate vence = LocalDate.now().plusDays(3);
        UsuarioAutorizado ua = new UsuarioAutorizado(4L, 400L, "Mariana", NACIMIENTO, "m@mail.com", "hash",
                Usuario.USUARIO_AUTORIZADO, inmueble, null, vence);

        assertThat(ua).isInstanceOf(Residente.class).isInstanceOf(NoAdmin.class);
        assertThat(ua.getInmueble()).isSameAs(inmueble);
        assertThat(ua.getVenceAutorizacion()).isEqualTo(vence);
        assertThat(ua.getTipoUsuario()).isEqualTo(Usuario.USUARIO_AUTORIZADO);
    }

    @Test
    @DisplayName("autorizacionVigente: vale hasta el dia de vencimiento inclusive; sin fecha es permanente")
    void autorizacionVigente() {
        UsuarioAutorizado ua = new UsuarioAutorizado();

        ua.setVenceAutorizacion(LocalDate.now().minusDays(1));
        assertThat(ua.autorizacionVigente()).isFalse();

        ua.setVenceAutorizacion(LocalDate.now());
        assertThat(ua.autorizacionVigente()).isTrue();

        ua.setVenceAutorizacion(null);
        assertThat(ua.autorizacionVigente()).isTrue();
    }

    @Test
    @DisplayName("Usuario nace inactivo, sin notificaciones leidas y con la fecha de hoy")
    void usuarioPorDefecto() {
        Usuario u = new Usuario(1L, 1L, "X", NACIMIENTO, "x@mail.com", "hash", Usuario.RESIDENTE);

        assertThat(u.isActive()).isFalse();
        assertThat(u.getNotificacionLeida()).isFalse();
        assertThat(u.getFechaCreacionCuenta()).isEqualTo(LocalDate.now());

        u.setToken("t");
        u.cerrarSesion();
        assertThat(u.getToken()).isNull();

        u.marcarNotificacionLeida();
        assertThat(u.getNotificacionLeida()).isTrue();
    }
}
