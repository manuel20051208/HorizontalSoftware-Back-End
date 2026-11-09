package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.model.Pago;
import unisimon.demo.policy.AccesoInmueblePolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.support.Proyecciones;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 8);

    @Mock
    private PagoRepository pagos;
    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private PasarelaPagoRepository pasarelas;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private LiquidacionService liquidacionService;
    @Mock
    private AccesoInmueblePolicy accesoInmueble;

    private PagoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
        servicio = new PagoServiceImpl(pagos, liquidaciones, pasarelas, residentes, inmuebles, liquidacionService,
                accesoInmueble, reloj);
    }

    private ContextoResidenteView residente(boolean activo) {
        return Proyecciones.de(ContextoResidenteView.class, "id", 5L, "active", activo, "inmuebleId", 1L,
                "direccionInmueble", "A-302", "inmuebleActive", true);
    }

    private PagoView pago(long id, String estado) {
        return Proyecciones.de(PagoView.class, "id", id, "inmuebleId", 1L, "direccionInmueble", "A-302",
                "monto", new BigDecimal("505000"), "fechaPago", HOY, "concepto", "Administracion A-302",
                "metodoPago", "PSE", "estado", estado, "referencia", "PAGO-" + id);
    }

    private PasarelaView pasarela() {
        return Proyecciones.de(PasarelaView.class, "id", 2L, "nombrePasarela", "Wompi",
                "urlApi", "https://checkout.wompi.co/p/", "apiKey", "pub_test_123", "estado", "ACTIVA");
    }

    @Test
    @DisplayName("registrarPago fija el valor con el saldo del servidor y devuelve la URL del checkout")
    void registrarPagoUsaSaldoDelServidor() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true)));
        when(liquidaciones.saldoPendiente(1L)).thenReturn(new BigDecimal("505000"));
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(pasarela()));
        when(pagos.insertar(any(Pago.class))).thenReturn(10L);
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.PENDIENTE)));

        PagoResponses.IniciarPago respuesta = servicio.registrarPago(5L, new PagoRequests.IniciarPago("pse"));

        ArgumentCaptor<Pago> insertado = ArgumentCaptor.forClass(Pago.class);
        verify(pagos).insertar(insertado.capture());
        assertThat(insertado.getValue().getMonto()).isEqualByComparingTo("505000");
        assertThat(insertado.getValue().getMetodoPago()).isEqualTo("PSE");
        assertThat(insertado.getValue().getEstado()).isEqualTo(Pago.PENDIENTE);
        verify(pagos).actualizarTransaccion(eq(10L), any(String.class), eq(2L));
        assertThat(respuesta.urlCheckout()).contains("amount-in-cents=50500000", "currency=COP");
    }

    @Test
    @DisplayName("registrarPago rechaza metodos que no son de la pasarela")
    void registrarPagoMetodoInvalido() {
        assertThatThrownBy(() -> servicio.registrarPago(5L, new PagoRequests.IniciarPago("EFECTIVO")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(pagos, never()).insertar(any());
    }

    @Test
    @DisplayName("registrarPago no deja pagar a una cuenta inactiva")
    void registrarPagoCuentaInactiva() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(false)));

        assertThatThrownBy(() -> servicio.registrarPago(5L, new PagoRequests.IniciarPago("PSE")))
                .isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("registrarPago falla si no hay saldo pendiente")
    void registrarPagoSinSaldo() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true)));
        when(liquidaciones.saldoPendiente(1L)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> servicio.registrarPago(5L, new PagoRequests.IniciarPago("PSE")))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("registrarPago falla si la pasarela no esta configurada")
    void registrarPagoSinPasarela() {
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true)));
        when(liquidaciones.saldoPendiente(1L)).thenReturn(new BigDecimal("1000"));
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarPago(5L, new PagoRequests.IniciarPago("PSE")))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("conciliar exige que el monto sea igual al saldo pendiente")
    void conciliarMontoDistinto() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(liquidaciones.saldoPendiente(1L)).thenReturn(new BigDecimal("505000"));

        assertThatThrownBy(() -> servicio.conciliarPagosAdministracion(1L,
                new PagoRequests.Conciliar(new BigDecimal("100"), "EFECTIVO", null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(pagos, never()).insertar(any());
    }

    @Test
    @DisplayName("conciliar registra el pago aprobado y deja al dia el inmueble")
    void conciliarCorrecto() {
        when(inmuebles.existePorId(1L)).thenReturn(true);
        when(liquidaciones.saldoPendiente(1L)).thenReturn(new BigDecimal("505000"));
        when(pagos.insertar(any(Pago.class))).thenReturn(11L);
        when(pagos.buscarPorId(11L)).thenReturn(Optional.of(pago(11, Pago.APROBADO)));

        servicio.conciliarPagosAdministracion(1L,
                new PagoRequests.Conciliar(new BigDecimal("505000"), "efectivo", "REC-1"));

        ArgumentCaptor<Pago> insertado = ArgumentCaptor.forClass(Pago.class);
        verify(pagos).insertar(insertado.capture());
        assertThat(insertado.getValue().getEstado()).isEqualTo(Pago.APROBADO);
        assertThat(insertado.getValue().getReferencia()).isEqualTo("REC-1");
        assertThat(insertado.getValue().getFechaPago()).isEqualTo(HOY);
        verify(liquidacionService).marcarInmueblePagado(1L);
    }

    @Test
    @DisplayName("aprobarPago de un pago pendiente lo aprueba con la fecha de hoy y marca el inmueble pagado")
    void aprobarPendiente() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.PENDIENTE)));
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(Optional.empty());

        servicio.aprobarPago(10L);

        verify(pagos).actualizarEstado(10L, Pago.APROBADO, HOY);
        verify(liquidacionService).marcarInmueblePagado(1L);
    }

    @Test
    @DisplayName("aprobarPago es idempotente: la pasarela puede avisar dos veces")
    void aprobarIdempotente() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.APROBADO)));

        servicio.aprobarPago(10L);

        verify(pagos, never()).actualizarEstado(any(), any(), any());
        verify(liquidacionService, never()).marcarInmueblePagado(any());
    }

    @Test
    @DisplayName("un pago anulado no puede aprobarse ni rechazarse")
    void estadosTerminales() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.ANULADO)));

        assertThatThrownBy(() -> servicio.aprobarPago(10L)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.rechazarPago(10L)).isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("rechazarPago deja el pago RECHAZADO sin fecha")
    void rechazarPendiente() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.PENDIENTE)));

        servicio.rechazarPago(10L);

        verify(pagos).actualizarEstado(10L, Pago.RECHAZADO, null);
    }

    @Test
    @DisplayName("anularPago solo funciona con pagos pendientes")
    void anularSoloPendientes() {
        when(pagos.buscarPorId(10L)).thenReturn(Optional.of(pago(10, Pago.PENDIENTE)));
        servicio.anularPago(10L);
        verify(pagos).actualizarEstado(10L, Pago.ANULADO, HOY);

        when(pagos.buscarPorId(11L)).thenReturn(Optional.of(pago(11, Pago.APROBADO)));
        assertThatThrownBy(() -> servicio.anularPago(11L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("un residente no ve pagos de otro inmueble")
    void pagoDeOtroInmueble() {
        PagoView ajeno = Proyecciones.de(PagoView.class, "id", 20L, "inmuebleId", 99L, "estado", Pago.APROBADO);
        when(pagos.buscarPorId(20L)).thenReturn(Optional.of(ajeno));
        when(residentes.buscarContexto(5L)).thenReturn(Optional.of(residente(true)));

        assertThatThrownBy(() -> servicio.consultarPagoDeResidente(5L, 20L)).isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("listar rechaza estados desconocidos y normaliza los validos")
    void listarEstados() {
        assertThatThrownBy(() -> servicio.listar(null, "XYZ")).isInstanceOf(SolicitudInvalidaException.class);

        servicio.listar(1L, " aprobado ");
        verify(pagos).listar(1L, Pago.APROBADO, PagoServiceImpl.LIMITE_LISTADO);
    }
}
