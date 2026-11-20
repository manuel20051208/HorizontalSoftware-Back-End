package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.dto.request.AutoRequests;
import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.InvitadoRequests;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.request.ParqueaderoRequests;
import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.request.ZonaRequests;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.factory.UsuarioFactory;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.model.Rompimiento;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.AutoRepository.AutoView;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.LiquidacionRepository.LiquidacionView;
import unisimon.demo.repository.NotificacionRepository;
import unisimon.demo.repository.NotificacionRepository.NotificacionView;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;
import unisimon.demo.repository.ReglaReservaRepository;
import unisimon.demo.repository.ReglaReservaRepository.ReglaView;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.RompimientoRepository;
import unisimon.demo.repository.RompimientoRepository.RompimientoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioDeInmuebleView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import unisimon.demo.service.CodigoVerificacionService;
import unisimon.demo.service.support.InmuebleDelResidente;
import unisimon.demo.service.support.UsuarioPersistidor;
import unisimon.demo.support.Proyecciones;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRUD que completa el diagrama de clases. Lo central: en una COMPOSICION el borrado del todo se lleva sus partes
 * (lo hace la base en cascada) y en una AGREGACION solo se quita el vinculo; una asociacion obligatoria impide
 * borrar mientras existan referencias (409).
 */
@ExtendWith(MockitoExtension.class)
class CrudSegunDiagramaTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private ParqueaderoRepository parqueaderos;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private ZonaComunRepository zonas;
    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private AutoRepository autos;
    @Mock
    private ReglaReservaRepository reglas;
    @Mock
    private RompimientoRepository rompimientos;
    @Mock
    private InvitadoRepository invitados;
    @Mock
    private NotificacionRepository notificaciones;
    @Mock
    private ReservaZonaComunRepository reservas;
    @Mock
    private InmuebleDelResidente inmuebleDelResidente;
    @Mock
    private UsuarioPersistidor persistidor;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private GeneradorContrasena generador;
    @Mock
    private CodigoVerificacionService codigos;

    // ------------------------------------------------------------------ fabricas de servicios

    private ParqueaderoServiceImpl parqueaderoService() {
        return new ParqueaderoServiceImpl(parqueaderos, inmuebles, autos);
    }

    private InmuebleServiceImpl inmuebleService() {
        return new InmuebleServiceImpl(inmuebles, parqueaderos, usuarios, zonas, liquidaciones,
                new EstadoPagoPolicy(RELOJ), autos);
    }

    private ZonaComunServiceImpl zonaService() {
        return new ZonaComunServiceImpl(zonas, inmuebles);
    }

    private ReglaReservaServiceImpl reglaService() {
        return new ReglaReservaServiceImpl(reglas, rompimientos, zonas, RELOJ);
    }

    private LiquidacionServiceImpl liquidacionService() {
        return new LiquidacionServiceImpl(liquidaciones, inmuebles, parqueaderos);
    }

    private UsuarioGestionServiceImpl usuarioService() {
        return new UsuarioGestionServiceImpl(usuarios, inmuebles, new UsuarioFactory(), persistidor, codificador,
                new PoliticaContrasena(), codigos, reglas, invitados, notificaciones);
    }

    private AutoServiceImpl autoService() {
        return new AutoServiceImpl(autos, parqueaderos, inmuebleDelResidente, inmuebles);
    }

    private InvitadoServiceImpl invitadoService() {
        return new InvitadoServiceImpl(invitados, usuarios, inmuebleDelResidente, persistidor, codificador,
                generador, RELOJ);
    }

    private NotificacionServiceImpl notificacionService() {
        return new NotificacionServiceImpl(notificaciones, usuarios, inmuebles, liquidaciones, reservas);
    }

    // ------------------------------------------------------------------ proyecciones

    private static ParqueaderoView parqueadero(Long id, Long inmuebleId) {
        return Proyecciones.de(ParqueaderoView.class, "id", id, "inmuebleId", inmuebleId,
                "valorMensual", new BigDecimal("80000"));
    }

    private static ZonaView zona() {
        return Proyecciones.de(ZonaView.class, "id", 7L, "nombreZonaComun", "Piscina", "capacidad", 10,
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(20, 0), "isActive", true,
                "reservasActuales", 0L);
    }

    private static ReglaView regla() {
        return Proyecciones.de(ReglaView.class, "id", 3L, "anticipacionMinimaHoras", 24, "duracionMaximaHoras", 4,
                "horasAntelacionPenalidad", 0, "tarifaPenalidad", BigDecimal.ZERO, "adminId", 1L);
    }

    private static RompimientoView rompimiento() {
        return Proyecciones.de(RompimientoView.class, "id", 11L, "zonaComunId", 7L, "nombreZonaComun", "Piscina",
                "reglaReservaId", 3L, "fechaAplicacion", LocalDate.of(2026, 11, 8), "activa", true,
                "observaciones", null);
    }

    private static LiquidacionView liquidacion(String estado) {
        return Proyecciones.de(LiquidacionView.class, "id", 20L, "inmuebleId", 1L, "direccionInmueble", "A-302",
                "periodo", LocalDate.of(2026, 11, 1), "fechaGeneracion", LocalDate.of(2026, 11, 1),
                "fechaLimite", LocalDate.of(2026, 11, 10), "cuotaAdministracion", new BigDecimal("200000"),
                "valorServiciosComunes", new BigDecimal("50000"), "valorMultas", BigDecimal.ZERO,
                "total", new BigDecimal("250000"), "estado", estado);
    }

    private static CredencialesView cuenta(Long id, String tipo, String correo) {
        return Proyecciones.de(CredencialesView.class, "id", id, "identificacion", 1000L + id,
                "nombreCompleto", "Persona " + id, "correoElectronico", correo, "contrasena", "hash",
                "tipoUsuario", tipo, "active", true, "token", null, "inmuebles", "1|A-302",
                "notificacionLeida", false, "fechaCreacionCuenta", LocalDate.of(2026, 1, 1),
                "correoVerificado", true);
    }

    private static AutoView auto(Long parqueaderoId) {
        return Proyecciones.de(AutoView.class, "id", 30L, "duenoId", 5L, "nombreDueno", "Ana", "placa", 123L,
                "parqueaderoId", parqueaderoId, "inmuebleId", 1L, "direccionInmueble", "A-302");
    }

    private static InvitadoView invitado() {
        return Proyecciones.de(InvitadoView.class, "id", 8L, "identificacion", 2008L, "nombreCompleto", "Mariana",
                "correoElectronico", "m@mail.com", "active", true, "venceAutorizacion", LocalDate.of(2026, 12, 31),
                "inmuebles", "1|A-302");
    }

    private static InmuebleBasicoView inmuebleBasico(Long id) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", id, "direccionInmueble", "A-302", "isActive", true);
    }

    // ------------------------------------------------------------------ Paqueadero

    @Test
    @DisplayName("Paqueadero: crea uno por inmueble y rechaza un segundo (409)")
    void parqueaderoCrear() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(parqueaderos.buscarPorInmueble(1L)).thenReturn(Optional.empty());
        when(parqueaderos.insertar(1L, new BigDecimal("80000"))).thenReturn(4L);
        when(parqueaderos.buscarPorId(4L)).thenReturn(Optional.of(parqueadero(4L, 1L)));

        var creado = parqueaderoService().crear(new ParqueaderoRequests.CrearParqueadero(1L, new BigDecimal("80000")));
        assertThat(creado.id()).isEqualTo(4L);

        when(parqueaderos.buscarPorInmueble(1L)).thenReturn(Optional.of(parqueadero(4L, 1L)));
        assertThatThrownBy(() -> parqueaderoService().crear(
                new ParqueaderoRequests.CrearParqueadero(1L, BigDecimal.ONE))).isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("Paqueadero: borrarlo deja al auto sin parqueadero (asociacion), no lo borra")
    void parqueaderoEliminarLiberaAuto() {
        when(parqueaderos.buscarPorId(4L)).thenReturn(Optional.of(parqueadero(4L, 1L)));

        parqueaderoService().eliminar(4L);

        InOrder orden = inOrder(autos, parqueaderos);
        orden.verify(autos).liberarParqueadero(4L);
        orden.verify(parqueaderos).eliminar(4L);
        verify(autos, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("Paqueadero: uno que no existe responde 404")
    void parqueaderoInexistente() {
        when(parqueaderos.buscarPorId(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> parqueaderoService().actualizar(9L,
                new ParqueaderoRequests.ActualizarParqueadero(BigDecimal.TEN)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ Inmueble

    @Test
    @DisplayName("Inmueble: borrar quita vinculos de agregacion y deja a la base las partes de composicion")
    void inmuebleEliminar() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(inmuebles.contarReservas(1L)).thenReturn(0L);
        when(usuarios.listarUsuariosDeInmueble(1L)).thenReturn(List.of(
                Proyecciones.de(UsuarioDeInmuebleView.class, "id", 5L),
                Proyecciones.de(UsuarioDeInmuebleView.class, "id", 6L)));
        when(usuarios.contarInmueblesDe(5L)).thenReturn(0L);   // se quedo sin inmuebles
        when(usuarios.contarInmueblesDe(6L)).thenReturn(1L);   // tiene otro

        inmuebleService().eliminar(1L);

        InOrder orden = inOrder(inmuebles, autos);
        orden.verify(inmuebles).limpiarZonas(1L);
        orden.verify(inmuebles).desvincularNotificaciones(1L);
        orden.verify(autos).liberarParqueaderosDeInmueble(1L);
        orden.verify(inmuebles).eliminar(1L);
        verify(usuarios).actualizarToken(5L, null);
        verify(usuarios, never()).actualizarToken(6L, null);
        verify(usuarios, never()).eliminar(anyLong());   // agregacion: los residentes no se borran
    }

    @Test
    @DisplayName("Inmueble: con reservas registradas no se borra (409)")
    void inmuebleConReservas() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(inmuebles.contarReservas(1L)).thenReturn(3L);

        assertThatThrownBy(() -> inmuebleService().eliminar(1L)).isInstanceOf(ConflictoException.class);
        verify(inmuebles, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("Inmueble: actualizar a una direccion ocupada responde 409; uno inexistente 404")
    void inmuebleActualizarRechazos() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(inmuebles.existePorDireccionEnOtro("B-101", 1L)).thenReturn(true);
        assertThatThrownBy(() -> inmuebleService().actualizar(1L,
                new InmuebleRequests.ActualizarInmueble(" b-101 ", true))).isInstanceOf(ConflictoException.class);

        when(inmuebles.existePorId(2L)).thenReturn(false);
        assertThatThrownBy(() -> inmuebleService().eliminar(2L)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(inmuebles, never()).actualizarDatos(any(), any(), any(Boolean.class));
    }

    // ------------------------------------------------------------------ ZonaComun

    @Test
    @DisplayName("ZonaComun: borrar quita solo el vinculo con los inmuebles (agregacion)")
    void zonaEliminar() {
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.contarReservas(7L)).thenReturn(0L);
        when(zonas.contarRompimientos(7L)).thenReturn(0L);

        zonaService().eliminar(7L);

        InOrder orden = inOrder(zonas);
        orden.verify(zonas).desvincularInmuebles(7L);
        orden.verify(zonas).eliminar(7L);
        verify(inmuebles, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("ZonaComun: con reservas o con reglas asignadas no se borra (409)")
    void zonaConReferencias() {
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.contarReservas(7L)).thenReturn(1L);
        assertThatThrownBy(() -> zonaService().eliminar(7L)).hasMessageContaining("reservas");

        when(zonas.contarReservas(7L)).thenReturn(0L);
        when(zonas.contarRompimientos(7L)).thenReturn(2L);
        assertThatThrownBy(() -> zonaService().eliminar(7L)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("reglas");
        verify(zonas, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("ZonaComun: actualizar guarda por columna y valida horario y nombre")
    void zonaActualizar() {
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(zonas.existePorNombreEnOtra("Piscina", 7L)).thenReturn(false);

        zonaService().actualizar(7L, new ZonaRequests.ActualizarZona(" Piscina ", 12, LocalTime.of(7, 0),
                LocalTime.of(21, 0)));
        verify(zonas).actualizarDatos(7L, "Piscina", 12, LocalTime.of(7, 0), LocalTime.of(21, 0));

        assertThatThrownBy(() -> zonaService().actualizar(7L, new ZonaRequests.ActualizarZona("Piscina", 12,
                LocalTime.NOON, LocalTime.NOON))).isInstanceOf(SolicitudInvalidaException.class);
    }

    // ------------------------------------------------------------------ ReglaReserva y Rompimiento

    @Test
    @DisplayName("ReglaReserva: aplicada a zonas no se borra (409); sin asignaciones si")
    void reglaEliminar() {
        when(reglas.buscarPorId(3L)).thenReturn(Optional.of(regla()));
        when(rompimientos.contarPorRegla(3L)).thenReturn(1L);
        assertThatThrownBy(() -> reglaService().eliminar(3L)).isInstanceOf(ConflictoException.class);
        verify(reglas, never()).eliminar(anyLong());

        when(rompimientos.contarPorRegla(3L)).thenReturn(0L);
        reglaService().eliminar(3L);
        verify(reglas).eliminar(3L);
    }

    @Test
    @DisplayName("Rompimiento: asignar una regla a una zona crea la asignacion activa; repetida responde 409")
    void rompimientoAsignar() {
        when(reglas.buscarPorId(3L)).thenReturn(Optional.of(regla()));
        when(zonas.buscarPorId(7L)).thenReturn(Optional.of(zona()));
        when(rompimientos.existePorZonaYRegla(7L, 3L)).thenReturn(false);
        when(rompimientos.insertar(any(Rompimiento.class))).thenReturn(11L);
        when(rompimientos.buscarPorId(11L)).thenReturn(Optional.of(rompimiento()));

        var asignado = reglaService().asignarZona(3L, new ReglaRequests.AsignarZona(7L, null));
        assertThat(asignado.activa()).isTrue();

        when(rompimientos.existePorZonaYRegla(7L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> reglaService().asignarZona(3L, new ReglaRequests.AsignarZona(7L, null)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("Rompimiento: borrarlo no borra la zona ni la regla")
    void rompimientoEliminar() {
        when(rompimientos.buscarPorId(11L)).thenReturn(Optional.of(rompimiento()));

        reglaService().eliminarRompimiento(11L);

        verify(rompimientos).eliminar(11L);
        verify(zonas, never()).eliminar(anyLong());
        verify(reglas, never()).eliminar(anyLong());
    }

    // ------------------------------------------------------------------ Liquidacion

    @Test
    @DisplayName("Liquidacion: actualizar recalcula el total con el dominio")
    void liquidacionActualizar() {
        when(liquidaciones.buscarPorId(20L)).thenReturn(Optional.of(liquidacion(Liquidacion.PENDIENTE)));

        liquidacionService().actualizar(20L, new LiquidacionRequests.Actualizar(LocalDate.of(2026, 11, 15),
                new BigDecimal("210000"), new BigDecimal("40000"), new BigDecimal("10000")));

        verify(liquidaciones).actualizarValores(20L, new BigDecimal("210000"), new BigDecimal("40000"),
                new BigDecimal("10000"), new BigDecimal("260000"));
        verify(liquidaciones).actualizarFechaLimite(20L, LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("Liquidacion: una pagada (AL_DIA) no se edita ni se borra (409)")
    void liquidacionPagada() {
        when(liquidaciones.buscarPorId(20L)).thenReturn(Optional.of(liquidacion(Liquidacion.AL_DIA)));

        assertThatThrownBy(() -> liquidacionService().eliminar(20L)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> liquidacionService().actualizar(20L, new LiquidacionRequests.Actualizar(
                LocalDate.of(2026, 11, 15), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO)))
                .isInstanceOf(ConflictoException.class);
        verify(liquidaciones, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("Liquidacion: al borrar la vigente, la mas reciente que queda pasa a ser la vigente")
    void liquidacionEliminarVigente() {
        when(liquidaciones.buscarPorId(20L)).thenReturn(Optional.of(liquidacion(Liquidacion.PENDIENTE)));
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(Optional.of(liquidacion(Liquidacion.PENDIENTE)));
        when(liquidaciones.buscarUltimaIdDeInmueble(1L)).thenReturn(Optional.of(19L));

        liquidacionService().eliminar(20L);

        verify(liquidaciones).eliminar(20L);
        verify(inmuebles).actualizarLiquidacionActual(1L, 19L);
    }

    // ------------------------------------------------------------------ Usuario

    @Test
    @DisplayName("Usuario: borrar quita los vinculos de agregacion y deja a la base la composicion (auto, reservas)")
    void usuarioEliminar() {
        when(usuarios.buscarCredencialesPorIdentificacion(1005L))
                .thenReturn(Optional.of(cuenta(5L, Usuario.RESIDENTE, "ana@mail.com")));

        usuarioService().eliminar(1L, 1005L);

        InOrder orden = inOrder(invitados, notificaciones, usuarios);
        orden.verify(invitados).desvincularTodos(5L);
        orden.verify(notificaciones).quitarDestinatario(5L);
        orden.verify(usuarios).eliminar(5L);
    }

    @Test
    @DisplayName("Usuario: el administrador no se borra a si mismo ni a otro que creo reglas (409)")
    void usuarioEliminarRechazos() {
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(cuenta(1L, Usuario.ADMINISTRADOR, "admin@mail.com")));
        assertThatThrownBy(() -> usuarioService().eliminar(1L, 1001L)).hasMessageContaining("propia");

        when(reglas.contarPorAdministrador(1L)).thenReturn(2L);
        assertThatThrownBy(() -> usuarioService().eliminar(99L, 1001L)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("reglas");
        verify(usuarios, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("Usuario: consultar devuelve nacimiento y contrato para prellenar el formulario de editar")
    void usuarioConsultarParaEditar() {
        when(usuarios.buscarCredencialesPorIdentificacion(1007L)).thenReturn(Optional.of(Proyecciones.de(
                CredencialesView.class, "id", 7L, "identificacion", 1007L, "nombreCompleto", "Vigilante",
                "correoElectronico", "vig@mail.com", "tipoUsuario", Usuario.VIGILANTE, "active", true,
                "fechaNacimiento", LocalDate.of(1990, 5, 1), "inicioContrato", LocalDate.of(2026, 1, 1),
                "venceContrato", LocalDate.of(2026, 12, 31))));

        var cuenta = usuarioService().consultar(1007L);

        assertThat(cuenta.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 1));
        assertThat(cuenta.inicioContrato()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(cuenta.venceContrato()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Usuario: cambiar el correo obliga a verificarlo de nuevo")
    void usuarioActualizarCorreo() {
        when(usuarios.buscarCredencialesPorIdentificacion(1005L))
                .thenReturn(Optional.of(cuenta(5L, Usuario.RESIDENTE, "ana@mail.com")));
        when(usuarios.existePorCorreoEnOtro("nueva@mail.com", 5L)).thenReturn(false);
        when(usuarios.buscarCredencialesPorId(5L))
                .thenReturn(Optional.of(cuenta(5L, Usuario.RESIDENTE, "nueva@mail.com")));

        usuarioService().actualizar(1005L, new UsuarioRequests.ActualizarUsuario("Ana Perez", null,
                " Nueva@Mail.com ", null, null, null));

        verify(usuarios).actualizarDatos(5L, "Ana Perez", null, "nueva@mail.com");
        verify(usuarios).reiniciarVerificacion(5L);
        verify(codigos).emitir(5L, "nueva@mail.com");
    }

    @Test
    @DisplayName("Usuario: un vigilante sin fechas de contrato o con correo ajeno es rechazado")
    void usuarioActualizarRechazos() {
        when(usuarios.buscarCredencialesPorIdentificacion(1007L))
                .thenReturn(Optional.of(cuenta(7L, Usuario.VIGILANTE, "vig@mail.com")));
        when(usuarios.existePorCorreoEnOtro("vig@mail.com", 7L)).thenReturn(false);
        assertThatThrownBy(() -> usuarioService().actualizar(1007L, new UsuarioRequests.ActualizarUsuario(
                "Vigilante", null, "vig@mail.com", null, null, null))).isInstanceOf(SolicitudInvalidaException.class);

        when(usuarios.existePorCorreoEnOtro("otro@mail.com", 7L)).thenReturn(true);
        assertThatThrownBy(() -> usuarioService().actualizar(1007L, new UsuarioRequests.ActualizarUsuario(
                "Vigilante", null, "otro@mail.com", null, null, null))).isInstanceOf(ConflictoException.class);
        verify(usuarios, never()).actualizarDatos(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ Auto

    @Test
    @DisplayName("Auto: actualizar a un parqueadero de otro inmueble responde 403")
    void autoParqueaderoAjeno() {
        when(autos.buscarPorDueno(5L)).thenReturn(Optional.of(auto(null)));
        when(autos.existePorPlacaEnOtro(123L, 30L)).thenReturn(false);
        when(parqueaderos.buscarPorId(4L)).thenReturn(Optional.of(parqueadero(4L, 2L)));
        when(inmuebleDelResidente.listar(5L)).thenReturn(List.of(inmuebleBasico(1L)));

        assertThatThrownBy(() -> autoService().actualizarAuto(5L, new AutoRequests.ActualizarAuto(123L, 4L)))
                .isInstanceOf(ProhibidoException.class);
        verify(autos, never()).actualizarDatos(any(), any(), any());
    }

    @Test
    @DisplayName("Auto: actualizar la placa y borrarlo (composicion con el residente)")
    void autoActualizarYEliminar() {
        when(autos.buscarPorDueno(5L)).thenReturn(Optional.of(auto(null)));
        when(autos.existePorPlacaEnOtro(456L, 30L)).thenReturn(false);
        when(autos.buscarPorId(30L)).thenReturn(Optional.of(auto(null)));

        autoService().actualizarAuto(5L, new AutoRequests.ActualizarAuto(456L, null));
        verify(autos).actualizarDatos(30L, 456L, null);

        autoService().eliminarAuto(5L);
        verify(autos).eliminar(30L);
    }

    @Test
    @DisplayName("Auto: placa repetida responde 409; sin auto registrado 404")
    void autoRechazos() {
        when(autos.buscarPorDueno(5L)).thenReturn(Optional.of(auto(null)));
        when(autos.existePorPlacaEnOtro(456L, 30L)).thenReturn(true);
        assertThatThrownBy(() -> autoService().actualizarAuto(5L, new AutoRequests.ActualizarAuto(456L, null)))
                .isInstanceOf(ConflictoException.class);

        when(autos.buscarPorDueno(6L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> autoService().eliminarAuto(6L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ UsuarioAutorizado

    @Test
    @DisplayName("Invitado: quitarlo borra solo el vinculo; si nadie mas lo autoriza queda inactivo")
    void invitadoEliminar() {
        when(invitados.buscarPorId(8L)).thenReturn(Optional.of(invitado()));
        when(invitados.esInvitadoDe(5L, 8L)).thenReturn(true);
        when(invitados.contarAnfitriones(8L)).thenReturn(0L);

        invitadoService().eliminarInvitado(5L, 8L);

        verify(invitados).desvincular(5L, 8L);
        verify(usuarios).actualizarActive(8L, false);
        verify(usuarios, never()).eliminar(anyLong());   // agregacion: el invitado no se borra
    }

    @Test
    @DisplayName("Invitado: si otro residente lo autoriza sigue activo; uno ajeno responde 403")
    void invitadoOtrosAnfitriones() {
        when(invitados.buscarPorId(8L)).thenReturn(Optional.of(invitado()));
        when(invitados.esInvitadoDe(5L, 8L)).thenReturn(true);
        when(invitados.contarAnfitriones(8L)).thenReturn(1L);
        invitadoService().eliminarInvitado(5L, 8L);
        verify(usuarios, never()).actualizarActive(8L, false);

        when(invitados.esInvitadoDe(9L, 8L)).thenReturn(false);
        assertThatThrownBy(() -> invitadoService().actualizarInvitado(9L, 8L,
                new InvitadoRequests.ActualizarInvitado("X", null, null))).isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("Invitado: actualizar con autorizacion vencida responde 400")
    void invitadoActualizarVencido() {
        when(invitados.buscarPorId(8L)).thenReturn(Optional.of(invitado()));
        when(invitados.esInvitadoDe(5L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> invitadoService().actualizarInvitado(5L, 8L,
                new InvitadoRequests.ActualizarInvitado("Mariana", null, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(invitados, never()).actualizarDatos(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ Notificacion

    @Test
    @DisplayName("Notificacion: borrar quita los vinculos con usuarios e inmuebles (agregacion)")
    void notificacionEliminar() {
        when(notificaciones.existePorId(15L)).thenReturn(true);

        notificacionService().eliminar(15L);

        InOrder orden = inOrder(notificaciones);
        orden.verify(notificaciones).desvincularUsuarios(15L);
        orden.verify(notificaciones).desvincularInmuebles(15L);
        orden.verify(notificaciones).eliminar(15L);
        verify(usuarios, never()).eliminar(anyLong());
    }

    @Test
    @DisplayName("Notificacion: consultar o borrar una inexistente responde 404")
    void notificacionInexistente() {
        when(notificaciones.existePorId(15L)).thenReturn(false);
        when(notificaciones.buscarPorId(16L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificacionService().eliminar(15L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> notificacionService().consultar(16L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        when(notificaciones.buscarPorId(17L)).thenReturn(Optional.of(Proyecciones.de(NotificacionView.class,
                "id", 17L, "mensaje", "Hola", "tipoNotificacion", "GENERAL",
                "fechaEnvio", LocalDateTime.of(2026, 11, 8, 10, 0))));
        assertThat(notificacionService().consultar(17L).mensaje()).isEqualTo("Hola");
    }
}
