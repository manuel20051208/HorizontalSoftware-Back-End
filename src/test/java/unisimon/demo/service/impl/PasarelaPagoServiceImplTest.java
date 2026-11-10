package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.bootstrap.PasarelaProperties;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.PasarelaNoDisponibleException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.integration.wompi.ClienteWompi;
import unisimon.demo.integration.wompi.FirmaEventosWompiTest;
import unisimon.demo.integration.wompi.TransaccionWompi;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private ClienteWompi clienteWompi;

    private static final String TX_ID = "1234-1610641025-49201";

    private PasarelaPagoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = servicioCon(FirmaEventosWompiTest.SECRETO);
    }

    private PasarelaPagoServiceImpl servicioCon(String secretoEventos) {
        PasarelaProperties config = new PasarelaProperties("Wompi", "https://checkout.wompi.co/p/", "pub_test_123",
                "test_integrity_abc", secretoEventos, "prv_test_abc", "https://sandbox.wompi.co/v1", null);
        return new PasarelaPagoServiceImpl(pasarelas, pagos, pagoService, liquidacionService, notificaciones,
                accesoInmueble, config, clienteWompi);
    }

    private PasarelaView pasarela() {
        return Proyecciones.de(PasarelaView.class, "id", 2L, "nombrePasarela", "Wompi",
                "urlApi", "https://checkout.wompi.co/p/", "apiKey", "pub_test_123", "estado", "ACTIVA");
    }

    private PagoView pago(String estado) {
        return Proyecciones.de(PagoView.class, "id", 10L, "inmuebleId", 1L, "direccionInmueble", "A-302",
                "monto", new BigDecimal("505000"), "estado", estado, "referencia", "PAGO-10");
    }

    private Map<String, Object> eventoFirmado() {
        return FirmaEventosWompiTest.evento("APPROVED", 50500000, FirmaEventosWompiTest.CHECKSUM);
    }

    private TransaccionWompi tx(String estado, long centavos) {
        return new TransaccionWompi(TX_ID, "PAGO-10", estado, centavos, "COP");
    }

    @Test
    @DisplayName("evento firmado + Wompi confirma APPROVED con el monto correcto: aprueba y notifica")
    void webhookAprueba() {
        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("APPROVED", 50500000));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        servicio.confirmarTransaccion(eventoFirmado());

        verify(pagoService).aprobarPago(10L);
        verify(notificaciones).notificarActualizacionDePago(1L);
    }

    @Test
    @DisplayName("el estado lo decide la API de Wompi, no el evento: DECLINED rechaza aunque el evento diga APPROVED")
    void webhookRechazaSegunApi() {
        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("DECLINED", 50500000));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        servicio.confirmarTransaccion(eventoFirmado());

        verify(pagoService).rechazarPago(10L);
        verify(pagoService, never()).aprobarPago(any());
    }

    @Test
    @DisplayName("APPROVED con monto distinto al del pago no aprueba (queda PENDIENTE para revision)")
    void webhookMontoDistinto() {
        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("APPROVED", 100));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));

        servicio.confirmarTransaccion(eventoFirmado());

        verify(pagoService, never()).aprobarPago(any());
        verify(notificaciones, never()).notificarActualizacionDePago(any());
    }

    @Test
    @DisplayName("PENDING no cambia nada; un evento repetido sobre un pago ya resuelto tampoco")
    void webhookPendienteYRepetido() {
        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("PENDING", 50500000));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.PENDIENTE)));
        servicio.confirmarTransaccion(eventoFirmado());

        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("APPROVED", 50500000));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.of(pago(Pago.APROBADO)));
        servicio.confirmarTransaccion(eventoFirmado());

        verify(pagoService, never()).aprobarPago(any());
        verify(pagoService, never()).rechazarPago(any());
        verify(notificaciones, never()).notificarActualizacionDePago(any());
    }

    @Test
    @DisplayName("evento sin firma valida: 401 sin consultar Wompi ni tocar pagos (el ataque con la llave publica ya no sirve)")
    void webhookFirmaInvalida() {
        Map<String, Object> falso = FirmaEventosWompiTest.evento("APPROVED", 50500000, "0".repeat(64));

        assertThatThrownBy(() -> servicio.confirmarTransaccion(falso)).isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.confirmarTransaccion(Map.of("referencia", "PAGO-10", "estado", "APROBADO")))
                .isInstanceOf(NoAutenticadoException.class);
        verifyNoInteractions(clienteWompi, pagos, pagoService);
    }

    @Test
    @DisplayName("sin secreto de eventos configurado el webhook no procesa nada")
    void webhookSinSecreto() {
        PasarelaPagoServiceImpl sinSecreto = servicioCon("");

        assertThatThrownBy(() -> sinSecreto.confirmarTransaccion(eventoFirmado()))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("eventos");
        verifyNoInteractions(clienteWompi, pagos);
    }

    @Test
    @DisplayName("otros tipos de evento se ignoran; referencia desconocida es 404; Wompi caido propaga 502")
    void webhookCasosBorde() {
        Map<String, Object> otro = eventoFirmado();
        otro.put("event", "nequi_token.updated");
        servicio.confirmarTransaccion(otro);
        verifyNoInteractions(clienteWompi);

        when(clienteWompi.consultarTransaccion(TX_ID)).thenReturn(tx("APPROVED", 50500000));
        when(pagos.buscarPorReferencia("PAGO-10")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.confirmarTransaccion(eventoFirmado()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        when(clienteWompi.consultarTransaccion(TX_ID)).thenThrow(new PasarelaNoDisponibleException("caido"));
        assertThatThrownBy(() -> servicio.confirmarTransaccion(eventoFirmado()))
                .isInstanceOf(PasarelaNoDisponibleException.class);
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
