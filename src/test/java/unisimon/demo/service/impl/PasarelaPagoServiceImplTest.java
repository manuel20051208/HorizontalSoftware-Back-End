package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.model.Pago;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.policy.AccesoInmueblePolicy;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.PagoService;
import unisimon.demo.support.Proyecciones;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasarelaPagoServiceImplTest {

    @Mock
    private PasarelaPagoRepository pasarelas;
    @Mock
    private PagoRepository pagos;
    @Mock
    private PagoService pagoService;
    @Mock
    private LiquidacionService liquidacionService;
    @Mock
    private NotificacionService notificaciones;
    @Mock
    private AccesoInmueblePolicy accesoInmueble;
    @InjectMocks
    private PasarelaPagoServiceImpl servicio;

    private PasarelaView pasarela() {
        return Proyecciones.de(PasarelaView.class, "id", 2L, "nombrePasarela", "Wompi",
                "urlApi", "https://checkout.wompi.co/p/", "apiKey", "pub_test_123", "estado", "ACTIVA");
    }

    private PagoView pago(String estado) {
        return Proyecciones.de(PagoView.class, "id", 10L, "inmuebleId", 1L, "direccionInmueble", "A-302",
                "monto", new BigDecimal("505000"), "estado", estado, "referencia", "PAGO-10");
    }

    @Test
    @DisplayName("el aviso con la llave correcta aprueba el pago y notifica el resultado")
    void webhookAprueba() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        servicio.confirmarTransaccion("pub_test_123", new PagoRequests.Webhook("PAGO-10", "aprobado"));

        verify(pagoService).aprobarPago(10L);
        verify(notificaciones).notificarActualizacionDePago(1L);
    }

    @Test
    @DisplayName("el aviso RECHAZADO rechaza el pago")
    void webhookRechaza() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        servicio.confirmarTransaccion("pub_test_123", new PagoRequests.Webhook("PAGO-10", "RECHAZADO"));

        verify(pagoService).rechazarPago(10L);
    }

    @Test
    @DisplayName("un aviso repetido no vuelve a notificar")
    void webhookRepetidoNoNotifica() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.APROBADO)));

        servicio.confirmarTransaccion("pub_test_123", new PagoRequests.Webhook("PAGO-10", "APROBADO"));

        verify(notificaciones, never()).notificarActualizacionDePago(any());
    }

    @Test
    @DisplayName("el aviso sin llave o con llave equivocada se rechaza antes de tocar el pago")
    void webhookLlaveInvalida() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));

        assertThatThrownBy(() -> servicio.confirmarTransaccion(null, new PagoRequests.Webhook("PAGO-10", "APROBADO")))
                .isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.confirmarTransaccion("otra", new PagoRequests.Webhook("PAGO-10", "APROBADO")))
                .isInstanceOf(NoAutenticadoException.class);
        verify(pagos, never()).buscarPorReferencia(any());
    }

    @Test
    @DisplayName("el aviso con referencia desconocida o estado invalido falla")
    void webhookDatosInvalidos() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));
        when(pagos.buscarPorReferencia("NO-EXISTE")).thenReturn(Optional.empty());
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        assertThatThrownBy(() -> servicio.confirmarTransaccion("pub_test_123",
                new PagoRequests.Webhook("NO-EXISTE", "APROBADO")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.confirmarTransaccion("pub_test_123",
                new PagoRequests.Webhook("PAGO-10", "TALVEZ")))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    @DisplayName("reembolsar un pago aprobado lo marca REEMBOLSADO, reabre la liquidacion y notifica")
    void reembolsarAprobado() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(Pago.APROBADO)));
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));

        servicio.reembolsar(10L);

        verify(pagos).actualizarEstado(eq(10L), eq(Pago.REEMBOLSADO), any());
        verify(liquidacionService).reabrirVigente(1L);
        verify(notificaciones).notificarActualizacionDePago(1L);
    }

    @Test
    @DisplayName("reembolsar un pago que no esta aprobado es un conflicto")
    void reembolsarPendiente() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(Pago.PENDIENTE)));
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));

        assertThatThrownBy(() -> servicio.reembolsar(10L)).isInstanceOf(ConflictoException.class);
        verify(liquidacionService, never()).reabrirVigente(any());
    }

    @Test
    @DisplayName("consultarEstado verifica el acceso al inmueble del pago")
    void consultarEstado() {
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.APROBADO)));
        UsuarioAutenticado usuario = new UsuarioAutenticado(5L, "RESIDENTE");

        assertThat(servicio.consultarEstado("PAGO-10", usuario).estado()).isEqualTo(Pago.APROBADO);
        verify(accesoInmueble).verificar(usuario, 1L);
    }

    @Test
    @DisplayName("configurar valida el estado y crea la pasarela si no existe")
    void configurar() {
        assertThatThrownBy(() -> servicio.configurar(
                new PagoRequests.ConfigurarPasarela("Wompi", "http://x", "k", "MAL")))
                .isInstanceOf(SolicitudInvalidaException.class);

        when(pasarelas.buscarPrincipal()).thenReturn(Optional.empty(), Optional.of(pasarela()));
        servicio.configurar(new PagoRequests.ConfigurarPasarela("Wompi", "http://x", "k", "activa"));

        verify(pasarelas).insertar(any(PasarelaPago.class));
    }
}
