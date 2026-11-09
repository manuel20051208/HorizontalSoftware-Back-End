package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacionTest {

    private Residente residente(long id, boolean activo, boolean invitado) {
        Residente r = invitado ? new UsuarioAutorizado() : new Residente();
        r.setId(id);
        r.setNombreCompleto("Persona " + id);
        r.setActive(activo);
        return r;
    }

    private Inmueble inmuebleConGente() {
        Inmueble inmueble = new Inmueble(1L, "A-302", null, true);
        inmueble.getUsuariosRelacionado().addAll(List.of(
                residente(1, true, false),      // titular activo: recibe
                residente(2, false, false),     // titular inactivo: no
                residente(3, true, true)));     // invitado: no
        Liquidacion l = new Liquidacion(1L, inmueble, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                new BigDecimal("485000"), BigDecimal.ZERO, BigDecimal.ZERO);
        inmueble.setLiquidacion(l);
        return inmueble;
    }

    @Test
    @DisplayName("enviarNotificacion falla sin destinatarios o sin mensaje")
    void sinDestinatariosONiMensaje() {
        assertThat(new Notificacion(null, List.of(), "hola", "GENERAL", null).enviarNotificacion()).isFalse();
        assertThat(new Notificacion(null, List.of(residente(1, true, false)), " ", "GENERAL", null)
                .enviarNotificacion()).isFalse();
    }

    @Test
    @DisplayName("enviarNotificacion fija la fecha de envio y deja a los destinatarios sin leer")
    void enviar() {
        Residente destinatario = residente(1, true, false);
        destinatario.setNotificacionLeida(true);
        Notificacion n = new Notificacion(null, List.of(destinatario), "hola", "GENERAL", null);

        assertThat(n.enviarNotificacion()).isTrue();
        assertThat(n.getFechaEnvio()).isNotNull();
        assertThat(destinatario.getNotificacionLeida()).isFalse();
    }

    @Test
    @DisplayName("notificarPagoPendiente avisa solo a los residentes titulares activos, con el valor y la fecha")
    void notificarPagoPendiente() {
        Notificacion n = new Notificacion();

        n.notificarPagoPendiente(inmuebleConGente());

        assertThat(n.getTipoNotificacion()).isEqualTo("PAGO_PENDIENTE");
        assertThat(n.getUsuarios()).extracting(Usuario::getId).containsExactly(1L);
        assertThat(n.getMensaje()).contains("485.000").contains("2026-11-10");
        assertThat(n.getFechaEnvio()).isNotNull();
    }

    @Test
    @DisplayName("notificarVisita y notificarActualizacionDePago usan a los residentes del inmueble")
    void otrasNotificaciones() {
        Notificacion visita = new Notificacion();
        visita.notificarVisita(inmuebleConGente());
        assertThat(visita.getTipoNotificacion()).isEqualTo("VISITA");
        assertThat(visita.getMensaje()).contains("A-302");

        Notificacion pago = new Notificacion();
        pago.notificarActualizacionDePago(inmuebleConGente(), List.of());
        assertThat(pago.getTipoNotificacion()).isEqualTo("PAGO_ACTUALIZADO");
        assertThat(pago.getUsuarios()).hasSize(1);
    }

    @Test
    @DisplayName("notificarReserva avisa al dueno de la reserva y nombra la zona")
    void notificarReserva() {
        ZonaComun zona = new ZonaComun();
        zona.setNombreZonaComun("Salon social");
        ReservaZonaComun reserva = new ReservaZonaComun(1L, residente(7, true, false), zona);
        Notificacion n = new Notificacion();

        n.notificarReserva(reserva);

        assertThat(n.getUsuarios()).extracting(Usuario::getId).containsExactly(7L);
        assertThat(n.getMensaje()).contains("Salon social");
    }

    @Test
    @DisplayName("sin residentes activos la notificacion queda sin enviar")
    void sinResidentes() {
        Notificacion n = new Notificacion();

        n.notificarPagoPendiente(new Inmueble(1L, "B-1", null, true));

        assertThat(n.getUsuarios()).isEmpty();
        assertThat(n.getFechaEnvio()).isNull();
    }
}
