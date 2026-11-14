package unisimon.demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Operaciones del diagrama de clases que viven en el modelo con su firma exacta: trabajan sobre los objetos
 * ya cargados, sin base de datos.
 */
class OperacionesDiagramaTest {

    private static final LocalDate HOY = LocalDate.now();

    private static Inmueble inmueble() {
        return new Inmueble(1L, "A-302", null, true);
    }

    private static Residente residente(long id, Inmueble inmueble) {
        Residente r = new Residente(id, 300L + id, "Residente " + id, null, "r" + id + "@mail.com", "hash",
                Usuario.RESIDENTE, inmueble, null);
        r.setActive(true);
        inmueble.getUsuariosRelacionado().add(r);
        return r;
    }

    private static UsuarioAutorizado invitado(long id, Inmueble inmueble, Residente anfitrion) {
        UsuarioAutorizado ua = new UsuarioAutorizado(id, 900L + id, "Invitado " + id, null, "i" + id + "@mail.com",
                "hash", Usuario.USUARIO_AUTORIZADO, inmueble, null, HOY.plusDays(5));
        ua.setActive(true);
        inmueble.getUsuariosRelacionado().add(ua);
        anfitrion.getInvitados().add(ua);
        return ua;
    }

    private static Administrador administrador() {
        Administrador a = new Administrador(1L, 100L, "Ana", null, "ana@mail.com", "hash", Usuario.ADMINISTRADOR,
                HOY.minusDays(10), HOY.plusDays(10));
        a.setActive(true);
        return a;
    }

    private static Vigilante vigilante() {
        Vigilante v = new Vigilante(2L, 200L, "Hernando", null, "h@mail.com", "hash", Usuario.VIGILANTE,
                HOY.minusDays(10), HOY.plusDays(10));
        v.setActive(true);
        return v;
    }

    private static ZonaComun zona(long id) {
        return new ZonaComun(id, "Zona " + id, 10, LocalTime.of(8, 0), LocalTime.of(22, 0));
    }

    private static Liquidacion liquidacion(Inmueble inmueble, String cuota) {
        Liquidacion l = new Liquidacion(1L, inmueble, HOY.withDayOfMonth(1), HOY.plusDays(5), new BigDecimal(cuota),
                BigDecimal.ZERO, BigDecimal.ZERO);
        inmueble.setLiquidacion(l);
        return l;
    }

    // ------------------------------------------------------------------ Usuario

    @Test
    @DisplayName("Usuario.iniciarSesion devuelve el usuario solo con cuenta activa y sus credenciales")
    void iniciarSesion() {
        Usuario u = new Usuario(1L, 100L, "Ana", null, "ana@mail.com", "hash", Usuario.RESIDENTE);

        assertThat(u.iniciarSesion("ana@mail.com", "hash")).isNull();          // inactiva
        u.setActive(true);
        assertThat(u.iniciarSesion(" ANA@mail.com ", "hash")).isSameAs(u);
        assertThat(u.iniciarSesion("ana@mail.com", "otra")).isNull();
        assertThat(u.iniciarSesion("otro@mail.com", "hash")).isNull();
    }

    @Test
    @DisplayName("Usuario.activarCuenta activa una cuenta inactiva con sus credenciales; si no, false")
    void activarCuenta() {
        Usuario u = new Usuario(1L, 100L, "Ana", null, "ana@mail.com", "hash", Usuario.RESIDENTE);

        assertThat(u.activarCuenta("ana@mail.com", "mala")).isFalse();
        assertThat(u.isActive()).isFalse();
        assertThat(u.activarCuenta("ana@mail.com", "hash")).isTrue();
        assertThat(u.isActive()).isTrue();
        assertThat(u.activarCuenta("ana@mail.com", "hash")).isFalse();         // ya estaba activa
    }

    @Test
    @DisplayName("Usuario.cambiarContrasena y recuperarContrasena cierran la sesion; cambiar exige cuenta propia y activa")
    void contrasenas() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        r.setToken("sesion");

        r.cambiarContrasena(inmueble, 3L);
        assertThat(r.getToken()).isNull();

        r.setToken("sesion");
        r.recuperarContrasena();
        assertThat(r.getToken()).isNull();

        assertThatThrownBy(() -> r.cambiarContrasena(inmueble, 99L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> r.cambiarContrasena(new Inmueble(2L, "B-101", null, true), 3L))
                .isInstanceOf(IllegalStateException.class);
        r.setActive(false);
        assertThatThrownBy(() -> r.cambiarContrasena(inmueble, 3L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Usuario.marcarNotificacionLeida solo la marca el propio usuario")
    void marcarNotificacionLeida() {
        Administrador a = administrador();

        a.marcarNotificacionLeida(null, 1L);
        assertThat(a.getNotificacionLeida()).isTrue();
        assertThatThrownBy(() -> a.marcarNotificacionLeida(null, 2L)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ Administrador

    @Test
    @DisplayName("Administrador.modificarActive alterna la cuenta de un NoAdmin y le cierra la sesion al desactivarla")
    void modificarActive() {
        Administrador admin = administrador();
        Residente r = residente(3L, inmueble());
        r.setToken("sesion");
        admin.setCuentas(List.of(r, administrador()));

        NoAdmin resultado = admin.modificarActive(r.getIdentificacion(), 1L);

        assertThat(resultado).isSameAs(r);
        assertThat(r.isActive()).isFalse();
        assertThat(r.getToken()).isNull();
        assertThat(((Usuario) admin.modificarActive(r.getIdentificacion(), 1L)).isActive()).isTrue();
        assertThatThrownBy(() -> admin.modificarActive(100L, 1L)).isInstanceOf(IllegalStateException.class);   // otro admin
        assertThatThrownBy(() -> admin.modificarActive(555L, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.modificarActive(r.getIdentificacion(), 9L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Administrador: configurar multas y tarifas recalcula la liquidacion vigente del inmueble")
    void configurarMultasYTarifas() {
        Administrador admin = administrador();
        Inmueble inmueble = inmueble();
        Liquidacion l = liquidacion(inmueble, "300000");

        l.setValorMultas(new BigDecimal("50000"));
        admin.configurarMultas(inmueble, 1L);
        assertThat(l.getTotal()).isEqualByComparingTo("350000");

        l.setCuotaAdministracion(new BigDecimal("320000"));
        admin.configurarTarifas(inmueble, 1L);
        assertThat(l.getTotal()).isEqualByComparingTo("370000");

        assertThatThrownBy(() -> admin.configurarMultas(inmueble(), 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> admin.configurarTarifas(inmueble, 2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Administrador: ConfigurarZonasComunes cambia el estado de las zonas y ConfigurarNuevaReglaReserva aplica sus reglas")
    void configurarZonasYReglas() {
        Administrador admin = administrador();
        Inmueble inmueble = inmueble();
        ZonaComun zona = zona(1L);
        inmueble.getZonaComunes().add(zona);

        admin.ConfigurarZonasComunes(inmueble, 1L);
        assertThat(zona.getIsActive()).isFalse();
        admin.ConfigurarZonasComunes(inmueble, 1L);
        assertThat(zona.getIsActive()).isTrue();

        ReglaReserva regla = new ReglaReserva(1L, 2, 4, 0, BigDecimal.ZERO, admin);
        Rompimiento asignacion = new Rompimiento();
        asignacion.setZonaComun(zona);
        asignacion.setReglaReserva(regla);
        asignacion.setActiva(false);
        zona.getReglasAsignadas().add(asignacion);

        admin.ConfigurarNuevaReglaReserva(inmueble, 1L);
        assertThat(asignacion.getActiva()).isTrue();
        assertThat(asignacion.getFechaAplicacion()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("Administrador.conciliarPagosAdministracion aprueba el pago pendiente si el monto es el de la liquidacion")
    void conciliarPagos() {
        Administrador admin = administrador();
        Inmueble inmueble = inmueble();
        Liquidacion l = liquidacion(inmueble, "300000");
        Pago pago = new Pago(1L, null, new BigDecimal("300000"), null, "Administracion", "EFECTIVO", "REF-1")
                .registrarPago(inmueble);

        assertThat(admin.conciliarPagosAdministracion(inmueble, 1L)).isSameAs(pago);
        assertThat(pago.getEstado()).isEqualTo(Pago.APROBADO);
        assertThat(l.getEstado()).isEqualTo(Liquidacion.AL_DIA);

        Inmueble otro = new Inmueble(2L, "B-101", null, true);
        liquidacion(otro, "300000");
        Pago corto = new Pago(2L, null, new BigDecimal("100"), null, "c", "EFECTIVO", "REF-2").registrarPago(otro);
        assertThatThrownBy(() -> admin.conciliarPagosAdministracion(otro, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(corto.getEstado()).isEqualTo(Pago.ANULADO);
    }

    @Test
    @DisplayName("Administrador: consultarIncidencias filtra por tipo; reportes y lectura exigen un administrador vigente")
    void incidenciasYReportes() {
        Administrador admin = administrador();
        Notificacion incidencia = new Notificacion(1L, List.of(admin), "Porton danado", Notificacion.INCIDENCIA, null);
        Notificacion general = new Notificacion(2L, List.of(admin), "Hola", "GENERAL", null);
        admin.setNotificaciones(List.of(incidencia, general));

        assertThat(admin.consultarIncidencias()).containsExactly(incidencia);
        admin.generarReportes();
        admin.consultarConfirmacionLectura();
        admin.gestionarCuentaUsuario();

        admin.setVenceContrato(HOY.minusDays(1));
        assertThatThrownBy(admin::generarReportes).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(admin::consultarIncidencias).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(admin::consultarConfirmacionLectura).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(admin::gestionarCuentaUsuario).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ Residente

    @Test
    @DisplayName("Residente: estado de cuenta, historial de pagos y notificaciones salen de su inmueble")
    void consultasDelResidente() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        Liquidacion l = liquidacion(inmueble, "300000");
        l.setValorMultas(new BigDecimal("20000"));
        Pago pago = new Pago(1L, null, BigDecimal.TEN, null, "c", "PSE", "REF").registrarPago(inmueble);
        Notificacion n = new Notificacion(1L, List.of(r), "Hola", "GENERAL", null);
        inmueble.getNotificaciones().add(n);

        assertThat(r.consultarEstadoCuenta(inmueble, 3L)).isSameAs(l);
        assertThat(l.getTotal()).isEqualByComparingTo("320000");
        assertThat(r.consultarHistorialPago(3L)).containsExactly(pago);
        assertThat(r.consultarNotificacion(inmueble, 3L)).containsExactly(n);

        assertThatThrownBy(() -> r.consultarEstadoCuenta(inmueble, 4L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.consultarNotificacion(new Inmueble(2L, "B-101", null, true), 3L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.consultarHistorialPago(4L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Residente: cambiarEstadoInvitado(s) solo cambia invitados propios")
    void invitados() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        Residente vecino = residente(4L, inmueble);
        UsuarioAutorizado propio = invitado(10L, inmueble, r);
        UsuarioAutorizado ajeno = invitado(11L, inmueble, vecino);

        assertThat(r.cambiarEstadoInvitado(propio, 10L)).isTrue();
        assertThat(propio.isActive()).isFalse();
        assertThat(r.cambiarEstadoInvitado(ajeno, 11L)).isFalse();
        assertThat(r.cambiarEstadoInvitado(propio, 99L)).isFalse();

        assertThat(r.cambiarEstadoInvitados(List.of(propio, ajeno))).isFalse();
        assertThat(propio.isActive()).isFalse();
        assertThat(r.cambiarEstadoInvitados(List.of(propio))).isTrue();
        assertThat(propio.isActive()).isTrue();

        assertThat(propio.buscarRelacionResidente()).containsExactly(r);
    }

    @Test
    @DisplayName("Residente: reservarZonaComun devuelve la zona si esta asignada y el residente habilitado")
    void reservarZonaComun() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        ZonaComun zona = zona(1L);
        inmueble.getZonaComunes().add(zona);

        assertThat(r.reservarZonaComun(1L)).isSameAs(zona);
        assertThatThrownBy(() -> r.reservarZonaComun(2L)).isInstanceOf(IllegalArgumentException.class);

        liquidacion(inmueble, "300000").marcarEnMora();
        assertThatThrownBy(() -> r.reservarZonaComun(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Residente: verReservas devuelve las suyas y cancelarReservaZonaComun solo cancela una que no ha empezado")
    void reservasDelResidente() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        ZonaComun zona = zona(1L);
        LocalDateTime ahora = LocalDateTime.now();
        ReservaZonaComun futura = new ReservaZonaComun(1L, r, zona, ahora.plusDays(1), ahora.plusDays(1).plusHours(2));
        ReservaZonaComun enCurso = new ReservaZonaComun(2L, r, zona, ahora.minusHours(1), ahora.plusHours(1));
        r.getReservas().addAll(List.of(futura, enCurso));

        assertThat(r.verReservas(3L)).containsExactly(futura, enCurso);
        assertThatThrownBy(() -> r.verReservas(4L)).isInstanceOf(IllegalArgumentException.class);

        r.cancelarReservaZonaComun(1L);
        assertThat(r.getReservas()).containsExactly(enCurso);
        assertThatThrownBy(() -> r.cancelarReservaZonaComun(2L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> r.cancelarReservaZonaComun(99L)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ Vigilante

    @Test
    @DisplayName("Vigilante.verificarAccesoZonaComun exige cuenta activa y una reserva en curso (propia o del anfitrion)")
    void verificarAcceso() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        Residente sinReserva = residente(4L, inmueble);
        UsuarioAutorizado ua = invitado(10L, inmueble, r);
        ZonaComun zona = zona(1L);
        LocalDateTime ahora = LocalDateTime.now();
        Vigilante v = vigilante();
        v.getReservas().add(new ReservaZonaComun(1L, r, zona, ahora.minusHours(1), ahora.plusHours(1)));
        v.getInvitados().add(ua);

        assertThat(v.verificarAccesoZonaComun(r)).isTrue();
        assertThat(v.verificarAccesoZonaComun(ua)).isTrue();
        assertThat(v.verificarAccesoZonaComun(sinReserva)).isFalse();
        assertThat(v.existeInvitado(ua.getIdentificacion())).isTrue();
        assertThat(v.existeInvitado(1L)).isFalse();
        assertThat(v.consultarReservas()).containsExactly(zona);

        ua.setVenceAutorizacion(HOY.minusDays(1));
        assertThat(v.verificarAccesoZonaComun(ua)).isFalse();
        r.setActive(false);
        assertThat(v.verificarAccesoZonaComun(r)).isFalse();
    }

    @Test
    @DisplayName("Vigilante.registrarIncidencia exige vigilante vigente y descripcion")
    void registrarIncidencia() {
        Vigilante v = vigilante();

        v.registrarIncidencia("Porton danado");
        assertThatThrownBy(() -> v.registrarIncidencia(" ")).isInstanceOf(IllegalArgumentException.class);
        v.setActive(false);
        assertThatThrownBy(() -> v.registrarIncidencia("Porton danado")).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ ReservaZonaComun, Auto, Liquidacion, Pago

    @Test
    @DisplayName("ReservaZonaComun: zonas del inmueble, disponibilidad y horario por hora")
    void reservaZonaComun() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        ZonaComun activa = zona(1L);
        ZonaComun inactiva = zona(2L);
        inactiva.cambiarEstado();
        inmueble.getZonaComunes().addAll(List.of(activa, inactiva));

        ReservaZonaComun reserva = new ReservaZonaComun(null, r, activa);
        assertThat(reserva.mostrarZonasComunes()).containsExactly(activa);
        assertThat(reserva.validarHorario(LocalTime.of(10, 0))).isTrue();
        assertThat(reserva.validarHorario(LocalTime.of(23, 0))).isFalse();
        assertThat(reserva.verificarDisponibilidad(LocalTime.of(10, 0))).isTrue();
        assertThat(new ReservaZonaComun(null, r, inactiva).verificarDisponibilidad(LocalTime.of(10, 0))).isFalse();
    }

    @Test
    @DisplayName("Auto.consultarDuenos devuelve los residentes titulares del inmueble del dueno")
    void consultarDuenos() {
        Inmueble inmueble = inmueble();
        Residente dueno = residente(3L, inmueble);
        Residente conyuge = residente(4L, inmueble);
        invitado(10L, inmueble, dueno);
        Auto auto = new Auto(7L, dueno, 123456L, null);

        assertThat(auto.consultarDuenos(inmueble, 7L)).containsExactly(dueno, conyuge);
        assertThat(auto.consultarDuenos(inmueble, 8L)).isEmpty();
        assertThat(auto.consultarDuenos(new Inmueble(2L, "B-101", null, true), 7L)).isEmpty();
    }

    @Test
    @DisplayName("Liquidacion.generarLiquidacion normaliza el periodo, pone la fecha limite y queda vigente en el inmueble")
    void generarLiquidacion() {
        Inmueble inmueble = inmueble();
        Liquidacion l = new Liquidacion(null, null, LocalDate.of(2026, 11, 14), null, new BigDecimal("300000"),
                new BigDecimal("50000"), null);

        assertThat(l.generarLiquidacion(inmueble)).isSameAs(l);
        assertThat(l.getPeriodo()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(l.getFechaLimite()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(l.getEstado()).isEqualTo(Liquidacion.PENDIENTE);
        assertThat(l.getTotal()).isEqualByComparingTo("350000");
        assertThat(inmueble.getLiquidacion()).isSameAs(l);
        assertThatThrownBy(() -> new Liquidacion().generarLiquidacion(new Inmueble(2L, "B-101", null, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Pago.registrarPago lo deja pendiente en el registro del inmueble; PasarelaPago consulta y notifica")
    void pagoYPasarela() {
        Inmueble inmueble = inmueble();
        Residente r = residente(3L, inmueble);
        Pago pago = new Pago(1L, null, new BigDecimal("300000"), null, "Administracion", "PSE", "REF-1");

        assertThat(pago.registrarPago(inmueble)).isSameAs(pago);
        assertThat(pago.getEstado()).isEqualTo(Pago.PENDIENTE);
        assertThat(pago.getFechaPago()).isEqualTo(HOY);
        assertThat(inmueble.getRegistroPagos()).containsExactly(pago);
        assertThatThrownBy(() -> new Pago(2L, null, BigDecimal.ZERO, null, "c", "PSE", null).registrarPago(inmueble))
                .isInstanceOf(IllegalArgumentException.class);

        PasarelaPago pasarela = new PasarelaPago(1L, "Wompi", "https://checkout.wompi.co/p/", "pub_test");
        pasarela.setSecretoIntegridad("secreto");
        pasarela.getPagos().add(pago);
        assertThat(pasarela.iniciarTransaccion(pago)).contains("reference=REF-1").contains("signature:integrity=");
        assertThat(pasarela.consultarEstado("REF-1")).isEqualTo(Pago.PENDIENTE);
        assertThat(pasarela.confirmarTransaccion("REF-1")).isFalse();
        pago.setEstado(Pago.APROBADO);
        assertThat(pasarela.confirmarTransaccion("REF-1")).isTrue();
        assertThat(pasarela.consultarEstado("NO-EXISTE")).isNull();

        pasarela.notificarResultado(pago);
        assertThat(inmueble.getNotificaciones()).singleElement()
                .satisfies(n -> assertThat(n.getUsuarios()).containsExactly(r));
    }
}
