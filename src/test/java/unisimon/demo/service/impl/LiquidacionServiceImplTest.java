package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.LiquidacionRepository.LiquidacionView;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;
import unisimon.demo.support.Proyecciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiquidacionServiceImplTest {

    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private ParqueaderoRepository parqueaderos;
    @InjectMocks
    private LiquidacionServiceImpl servicio;

    private InmuebleBasicoView inmueble(boolean activo) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", 1L, "direccionInmueble", "A-302", "isActive", activo);
    }

    private LiquidacionView liquidacion(long id, String estado, String cuota, String servicios, String multas,
                                        LocalDate limite) {
        BigDecimal total = new BigDecimal(cuota).add(new BigDecimal(servicios)).add(new BigDecimal(multas));
        return Proyecciones.de(LiquidacionView.class, "id", id, "inmuebleId", 1L, "direccionInmueble", "A-302",
                "periodo", LocalDate.of(2026, 11, 1), "fechaGeneracion", LocalDate.of(2026, 11, 1),
                "fechaLimite", limite, "cuotaAdministracion", new BigDecimal(cuota),
                "valorServiciosComunes", new BigDecimal(servicios), "valorMultas", new BigDecimal(multas),
                "total", total, "estado", estado);
    }

    @Test
    @DisplayName("generarLiquidacion copia las tarifas anteriores, fija el dia 10 y deja la nueva como vigente")
    void generarCopiandoTarifas() {
        when(inmuebles.buscarBasico(1L)).thenReturn(Optional.of(inmueble(true)));
        when(liquidaciones.existePorInmuebleYPeriodo(1L, LocalDate.of(2026, 12, 1))).thenReturn(false);
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "485000", "20000", "30000", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.insertar(any(Liquidacion.class))).thenReturn(9L);
        when(liquidaciones.buscarPorId(9L)).thenReturn(
                Optional.of(liquidacion(9, Liquidacion.PENDIENTE, "485000", "20000", "0", LocalDate.of(2026, 12, 10))));

        LiquidacionResponses.Liquidacion respuesta =
                servicio.generarLiquidacion(new LiquidacionRequests.Generar(1L, LocalDate.of(2026, 12, 15), null, null, null));

        ArgumentCaptor<Liquidacion> captor = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).insertar(captor.capture());
        Liquidacion nueva = captor.getValue();
        assertThat(nueva.getPeriodo()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(nueva.getFechaLimite()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(nueva.getCuotaAdministracion()).isEqualByComparingTo("485000");
        assertThat(nueva.getValorServiciosComunes()).isEqualByComparingTo("20000");
        assertThat(nueva.getValorMultas()).isEqualByComparingTo("0");     // las multas no se arrastran
        assertThat(nueva.getTotal()).isEqualByComparingTo("505000");
        assertThat(nueva.getEstado()).isEqualTo(Liquidacion.PENDIENTE);
        verify(liquidaciones).actualizarEstado(7L, Liquidacion.EN_MORA);   // lo que no se pago del mes anterior
        verify(inmuebles).actualizarLiquidacionActual(1L, 9L);
        assertThat(respuesta.id()).isEqualTo(9L);
    }

    @Test
    @DisplayName("si el mes anterior estaba pagado no se toca; las tarifas del request mandan sobre las anteriores")
    void generarConTarifasNuevas() {
        when(inmuebles.buscarBasico(1L)).thenReturn(Optional.of(inmueble(true)));
        when(liquidaciones.existePorInmuebleYPeriodo(any(), any())).thenReturn(false);
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.AL_DIA, "400000", "0", "0", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.insertar(any(Liquidacion.class))).thenReturn(9L);
        when(liquidaciones.buscarPorId(9L)).thenReturn(
                Optional.of(liquidacion(9, Liquidacion.PENDIENTE, "500000", "10000", "0", LocalDate.of(2026, 12, 5))));

        servicio.generarLiquidacion(new LiquidacionRequests.Generar(1L, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5), new BigDecimal("500000"), new BigDecimal("10000")));

        ArgumentCaptor<Liquidacion> captor = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).insertar(captor.capture());
        assertThat(captor.getValue().getCuotaAdministracion()).isEqualByComparingTo("500000");
        assertThat(captor.getValue().getFechaLimite()).isEqualTo(LocalDate.of(2026, 12, 5));
        verify(liquidaciones, never()).actualizarEstado(eq(7L), any());
    }

    @Test
    @DisplayName("generarLiquidacion rechaza periodo repetido, inmueble inactivo o inexistente y falta de tarifas")
    void generarRechazos() {
        when(inmuebles.buscarBasico(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.generarLiquidacion(
                new LiquidacionRequests.Generar(404L, LocalDate.now(), null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        when(inmuebles.buscarBasico(1L)).thenReturn(Optional.of(inmueble(false)));
        assertThatThrownBy(() -> servicio.generarLiquidacion(
                new LiquidacionRequests.Generar(1L, LocalDate.now(), null, null, null)))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");

        when(inmuebles.buscarBasico(1L)).thenReturn(Optional.of(inmueble(true)));
        when(liquidaciones.existePorInmuebleYPeriodo(1L, LocalDate.of(2026, 11, 1))).thenReturn(true);
        assertThatThrownBy(() -> servicio.generarLiquidacion(
                new LiquidacionRequests.Generar(1L, LocalDate.of(2026, 11, 20), null, null, null)))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya tiene liquidacion");

        when(liquidaciones.existePorInmuebleYPeriodo(1L, LocalDate.of(2027, 1, 1))).thenReturn(false);
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.generarLiquidacion(
                new LiquidacionRequests.Generar(1L, LocalDate.of(2027, 1, 5), null, null, null)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("cuotaAdministracion");
        verify(liquidaciones, never()).insertar(any());
    }

    @Test
    @DisplayName("generarMasiva genera donde puede y reporta los inmuebles omitidos con su motivo")
    void generarMasiva() {
        when(inmuebles.listarIdsActivos()).thenReturn(List.of(1L, 2L, 3L));
        LocalDate periodo = LocalDate.of(2026, 12, 1);
        when(liquidaciones.existePorInmuebleYPeriodo(1L, periodo)).thenReturn(true);
        when(liquidaciones.existePorInmuebleYPeriodo(2L, periodo)).thenReturn(false);
        when(liquidaciones.existePorInmuebleYPeriodo(3L, periodo)).thenReturn(false);
        when(liquidaciones.buscarVigenteDeInmueble(2L)).thenReturn(Optional.empty());
        when(liquidaciones.buscarVigenteDeInmueble(3L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.AL_DIA, "485000", "0", "0", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.insertar(any(Liquidacion.class))).thenReturn(9L);
        when(liquidaciones.buscarPorId(9L)).thenReturn(
                Optional.of(liquidacion(9, Liquidacion.PENDIENTE, "485000", "0", "0", LocalDate.of(2026, 12, 10))));

        LiquidacionResponses.ResultadoMasiva resultado =
                servicio.generarMasiva(new LiquidacionRequests.GenerarMasiva(LocalDate.of(2026, 12, 20), null));

        assertThat(resultado.generadas()).isEqualTo(1);
        assertThat(resultado.omitidas()).extracting(LiquidacionResponses.Omitida::inmuebleId).containsExactly(1L, 2L);
        verify(inmuebles).actualizarLiquidacionActual(3L, 9L);
    }

    @Test
    @DisplayName("configurarTarifas recalcula el total de la liquidacion PENDIENTE y actualiza el parqueadero")
    void configurarTarifas() {
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "100", "10", "5", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.buscarPorId(7L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "200", "20", "5", LocalDate.of(2026, 11, 10))));
        when(parqueaderos.buscarPorInmueble(1L)).thenReturn(Optional.of(Proyecciones.de(ParqueaderoView.class, "id", 1L)));

        servicio.configurarTarifas(1L, new InmuebleRequests.Tarifas(new BigDecimal("200"), new BigDecimal("20"),
                new BigDecimal("90000")));

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(liquidaciones).actualizarValores(eq(7L), any(), any(), any(), total.capture());
        assertThat(total.getValue()).isEqualByComparingTo("225");   // 200 + 20 + 5 de multas
        verify(parqueaderos).actualizarValorMensual(eq(1L), any());
    }

    @Test
    @DisplayName("configurarTarifas crea el parqueadero si no existe y solo ajusta liquidaciones PENDIENTES")
    void configurarTarifasReglas() {
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.buscarPorId(7L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.of(2026, 11, 10))));
        when(parqueaderos.buscarPorInmueble(1L)).thenReturn(Optional.empty());

        servicio.configurarTarifas(1L, new InmuebleRequests.Tarifas(BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5")));
        verify(parqueaderos).insertar(eq(1L), any());

        when(liquidaciones.buscarVigenteDeInmueble(2L)).thenReturn(
                Optional.of(liquidacion(8, Liquidacion.AL_DIA, "100", "0", "0", LocalDate.of(2026, 11, 10))));
        assertThatThrownBy(() -> servicio.configurarTarifas(2L,
                new InmuebleRequests.Tarifas(BigDecimal.TEN, BigDecimal.ONE, null)))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("PENDIENTE");

        when(liquidaciones.buscarVigenteDeInmueble(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.configurarMultas(3L, new InmuebleRequests.Multas(BigDecimal.TEN)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("configurarMultas suma las multas al total")
    void configurarMultas() {
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "485000", "0", "0", LocalDate.of(2026, 11, 10))));
        when(liquidaciones.buscarPorId(7L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "485000", "0", "50000", LocalDate.of(2026, 11, 10))));

        servicio.configurarMultas(1L, new InmuebleRequests.Multas(new BigDecimal("50000")));

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(liquidaciones).actualizarValores(eq(7L), any(), any(), any(), total.capture());
        assertThat(total.getValue()).isEqualByComparingTo("535000");
    }

    @Test
    @DisplayName("marcarEnMora y marcarAlDia usan el dominio y guardan solo el estado")
    void marcarEstados() {
        when(liquidaciones.buscarPorId(7L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.of(2026, 11, 10))));

        servicio.marcarEnMora(7L);
        verify(liquidaciones).actualizarEstado(7L, Liquidacion.EN_MORA);

        servicio.marcarAlDia(7L);
        verify(liquidaciones).actualizarEstado(7L, Liquidacion.AL_DIA);
    }

    @Test
    @DisplayName("marcarVencidas pasa a EN_MORA solo las PENDIENTES cuya fecha limite ya paso")
    void marcarVencidas() {
        when(liquidaciones.listarPorEstado(Liquidacion.PENDIENTE)).thenReturn(List.of(
                liquidacion(1, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.now().minusDays(1)),
                liquidacion(2, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.now().plusDays(1)),
                liquidacion(3, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.now())));

        int cambiadas = servicio.marcarVencidas();

        assertThat(cambiadas).isEqualTo(1);
        verify(liquidaciones).actualizarEstadoPorIds(List.of(1L), Liquidacion.EN_MORA);
    }

    @Test
    @DisplayName("marcarVencidas no escribe nada si ninguna vencio")
    void marcarVencidasNinguna() {
        when(liquidaciones.listarPorEstado(Liquidacion.PENDIENTE)).thenReturn(List.of(
                liquidacion(2, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.now().plusDays(1))));

        assertThat(servicio.marcarVencidas()).isZero();
        verify(liquidaciones, never()).actualizarEstadoPorIds(any(), any());
    }

    @Test
    @DisplayName("marcarInmueblePagado deja al dia todo lo que el inmueble debia")
    void marcarInmueblePagado() {
        servicio.marcarInmueblePagado(1L);

        verify(liquidaciones).marcarPagadasDeInmueble(1L);
    }

    @Test
    @DisplayName("reabrirVigente: un pago reembolsado vuelve a PENDIENTE, o a EN_MORA si ya vencio")
    void reabrirVigente() {
        when(liquidaciones.buscarVigenteDeInmueble(1L)).thenReturn(
                Optional.of(liquidacion(7, Liquidacion.AL_DIA, "100", "0", "0", LocalDate.now().plusDays(3))));
        servicio.reabrirVigente(1L);
        verify(liquidaciones).actualizarEstado(7L, Liquidacion.PENDIENTE);

        when(liquidaciones.buscarVigenteDeInmueble(2L)).thenReturn(
                Optional.of(liquidacion(8, Liquidacion.AL_DIA, "100", "0", "0", LocalDate.now().minusDays(3))));
        servicio.reabrirVigente(2L);
        verify(liquidaciones).actualizarEstado(8L, Liquidacion.EN_MORA);

        when(liquidaciones.buscarVigenteDeInmueble(3L)).thenReturn(
                Optional.of(liquidacion(9, Liquidacion.PENDIENTE, "100", "0", "0", LocalDate.now().plusDays(3))));
        servicio.reabrirVigente(3L);
        verify(liquidaciones, never()).actualizarEstado(eq(9L), any());
    }

    @Test
    @DisplayName("listar valida el estado pedido y normaliza el periodo al dia 1")
    void listar() {
        SolicitudPagina pagina = SolicitudPagina.primera();
        assertThatThrownBy(() -> servicio.listar(null, "RARO", null, pagina))
                .isInstanceOf(SolicitudInvalidaException.class);

        LocalDate primerDia = LocalDate.of(2026, 11, 1);
        when(liquidaciones.listar(1L, "PENDIENTE", primerDia, 20, 0L)).thenReturn(List.of());
        when(liquidaciones.contar(1L, "PENDIENTE", primerDia)).thenReturn(0L);
        Pagina<LiquidacionResponses.Liquidacion> resultado =
                servicio.listar(1L, " pendiente ", LocalDate.of(2026, 11, 20), pagina);
        assertThat(resultado.contenido()).isEmpty();
        assertThat(resultado.totalPaginas()).isZero();
    }
}
