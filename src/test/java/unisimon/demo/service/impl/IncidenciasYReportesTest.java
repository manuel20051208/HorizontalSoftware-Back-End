package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReporteResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.model.Notificacion;
import unisimon.demo.model.Pago;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.NotificacionRepository;
import unisimon.demo.repository.NotificacionRepository.NotificacionView;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.TotalInmuebleView;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.support.Proyecciones;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Casos de uso agregados al diagrama de clases: Vigilante.registrarIncidencia, Administrador.consultarIncidencias,
 * Administrador.generarReportes y el efecto de Usuario.preferenciasNotificacion al enviar.
 */
@ExtendWith(MockitoExtension.class)
class IncidenciasYReportesTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private NotificacionRepository notificaciones;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private ReservaZonaComunRepository reservas;
    @Mock
    private InvitadoRepository invitados;
    @Mock
    private PagoRepository pagos;
    @Mock
    private NotificacionService servicioNotificaciones;

    private VigilanteServiceImpl vigilante() {
        return new VigilanteServiceImpl(reservas, invitados, usuarios, servicioNotificaciones, RELOJ);
    }

    private NotificacionServiceImpl notificacionService() {
        return new NotificacionServiceImpl(notificaciones, usuarios, inmuebles, liquidaciones, reservas);
    }

    // ------------------------------------------------------------------ Vigilante.registrarIncidencia

    @Test
    @DisplayName("registrarIncidencia envia una notificacion INCIDENCIA a los administradores activos")
    void registrarIncidencia() {
        when(usuarios.buscarCredencialesPorId(7L))
                .thenReturn(Optional.of(Proyecciones.de(CredencialesView.class, "id", 7L, "nombreCompleto", "Hernando",
                        "tipoUsuario", Usuario.VIGILANTE, "active", true)));
        when(usuarios.listar(Usuario.ADMINISTRADOR, true)).thenReturn(List.of(
                Proyecciones.de(UsuarioListaView.class, "id", 1L), Proyecciones.de(UsuarioListaView.class, "id", 2L)));
        NotificacionResponses.Notificacion creada = new NotificacionResponses.Notificacion(50L, "m",
                Notificacion.INCIDENCIA, LocalDateTime.now(RELOJ));
        when(servicioNotificaciones.enviarNotificacion(any())).thenReturn(creada);

        NotificacionResponses.Notificacion resultado = vigilante().registrarIncidencia(7L, "  Porton danado ");

        assertThat(resultado).isSameAs(creada);
        ArgumentCaptor<NotificacionRequests.Enviar> enviado = ArgumentCaptor.forClass(NotificacionRequests.Enviar.class);
        verify(servicioNotificaciones).enviarNotificacion(enviado.capture());
        assertThat(enviado.getValue().tipoNotificacion()).isEqualTo("INCIDENCIA");
        assertThat(enviado.getValue().usuarioIds()).containsExactly(1L, 2L);
        assertThat(enviado.getValue().mensaje()).isEqualTo("Incidencia reportada por Hernando: Porton danado");
        assertThat(enviado.getValue().inmuebleId()).isNull();
    }

    @Test
    @DisplayName("registrarIncidencia falla con 409 si no hay un administrador activo que la reciba")
    void registrarIncidenciaSinAdministrador() {
        when(usuarios.buscarCredencialesPorId(7L))
                .thenReturn(Optional.of(Proyecciones.de(CredencialesView.class, "id", 7L, "nombreCompleto", "Hernando",
                        "tipoUsuario", Usuario.VIGILANTE, "active", true)));
        when(usuarios.listar(Usuario.ADMINISTRADOR, true)).thenReturn(List.of());

        assertThatThrownBy(() -> vigilante().registrarIncidencia(7L, "Porton danado"))
                .isInstanceOf(ConflictoException.class);
        verifyNoInteractions(servicioNotificaciones);
    }

    // ------------------------------------------------------------------ Administrador.consultarIncidencias

    @Test
    @DisplayName("consultarIncidencias lista solo las notificaciones de tipo INCIDENCIA, paginadas")
    void consultarIncidencias() {
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 14, 9, 30);
        when(notificaciones.listarPorTipo("INCIDENCIA", 20, 0L)).thenReturn(List.of(Proyecciones.de(
                NotificacionView.class, "id", 50L, "mensaje", "Incidencia reportada por Hernando: Porton danado",
                "tipoNotificacion", "INCIDENCIA", "fechaEnvio", fecha)));
        when(notificaciones.contarPorTipo("INCIDENCIA")).thenReturn(1L);

        Pagina<NotificacionResponses.Notificacion> pagina = notificacionService()
                .consultarIncidencias(SolicitudPagina.primera());

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.contenido()).singleElement().satisfies(n -> {
            assertThat(n.id()).isEqualTo(50L);
            assertThat(n.tipoNotificacion()).isEqualTo("INCIDENCIA");
            assertThat(n.fechaEnvio()).isEqualTo(fecha);
        });
    }

    // ------------------------------------------------------------------ preferencias al enviar

    @Test
    @DisplayName("una notificacion solo llega a quienes aceptan ese tipo en sus preferencias")
    void enviarRespetaPreferencias() {
        when(usuarios.filtrarPorPreferencia(List.of(1L, 2L), "RESERVA")).thenReturn(List.of(2L));
        when(notificaciones.insertar(any())).thenReturn(60L);

        notificacionService().enviarNotificacion(new NotificacionRequests.Enviar("Hola", "reserva", List.of(1L, 2L), null));

        verify(notificaciones).agregarDestinatarios(60L, List.of(2L));
        verify(usuarios).marcarNoLeida(List.of(2L));
    }

    @Test
    @DisplayName("si nadie acepta ese tipo no se guarda nada y responde 409")
    void enviarSinInteresados() {
        when(usuarios.filtrarPorPreferencia(List.of(1L), "GENERAL")).thenReturn(List.of());

        assertThatThrownBy(() -> notificacionService()
                .enviarNotificacion(new NotificacionRequests.Enviar("Hola", null, List.of(1L), null)))
                .isInstanceOf(ConflictoException.class);
        verify(notificaciones, never()).insertar(any());
    }

    @Test
    @DisplayName("las incidencias llegan siempre: no pasan por el filtro de preferencias")
    void incidenciaIgnoraPreferencias() {
        when(notificaciones.insertar(any())).thenReturn(61L);

        notificacionService().enviarNotificacion(
                new NotificacionRequests.Enviar("Porton danado", "INCIDENCIA", List.of(1L), null));

        verify(usuarios, never()).filtrarPorPreferencia(anyList(), anyString());
        verify(notificaciones).agregarDestinatarios(61L, List.of(1L));
    }

    // ------------------------------------------------------------------ Administrador.generarReportes

    private static InmuebleResumenView inmueble(long id, String direccion, long liquidaciones, String saldo,
                                                LocalDate limite, boolean mora) {
        return Proyecciones.de(InmuebleResumenView.class, "id", id, "direccionInmueble", direccion,
                "totalLiquidaciones", liquidaciones, "saldoPendiente", new BigDecimal(saldo),
                "fechaLimitePendiente", limite, "tieneMora", mora);
    }

    @Test
    @DisplayName("generarReportes consolida recaudo, mora y pendientes inmueble por inmueble")
    void generarReportes() {
        when(inmuebles.listarResumen()).thenReturn(List.of(
                inmueble(1L, "A-101", 2, "0", null, false),                               // al dia
                inmueble(2L, "A-102", 1, "300000", LocalDate.of(2026, 11, 1), true),      // en mora
                inmueble(3L, "A-103", 1, "250000", LocalDate.of(2026, 11, 30), false),    // pendiente, en plazo
                inmueble(4L, "A-104", 0, "0", null, false)));                             // sin liquidacion
        when(pagos.sumarPorInmueble(Pago.APROBADO)).thenReturn(List.of(
                Proyecciones.de(TotalInmuebleView.class, "inmuebleId", 1L, "total", new BigDecimal("500000")),
                Proyecciones.de(TotalInmuebleView.class, "inmuebleId", 2L, "total", new BigDecimal("100000"))));
        PanelAdministradorServiceImpl panel = new PanelAdministradorServiceImpl(inmuebles, pagos,
                new EstadoPagoPolicy(RELOJ), RELOJ);

        ReporteResponses.Consolidado reporte = panel.generarReportes();

        assertThat(reporte.fechaGeneracion()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(reporte.totalInmuebles()).isEqualTo(4);
        assertThat(reporte.inmueblesAlDia()).isEqualTo(1);
        assertThat(reporte.inmueblesEnMora()).isEqualTo(1);
        assertThat(reporte.inmueblesPendientes()).isEqualTo(1);
        assertThat(reporte.recaudo()).isEqualByComparingTo("600000");
        assertThat(reporte.mora()).isEqualByComparingTo("300000");
        assertThat(reporte.pendiente()).isEqualByComparingTo("250000");
        assertThat(reporte.inmuebles()).extracting(ReporteResponses.Inmueble::estadoPago)
                .containsExactly("AL_DIA", "VENCIDA", "POR_VENCER", "SIN_LIQUIDACION");
        assertThat(reporte.inmuebles().get(1).recaudado()).isEqualByComparingTo("100000");
        assertThat(reporte.inmuebles().get(2).recaudado()).isEqualByComparingTo("0");
    }
}
