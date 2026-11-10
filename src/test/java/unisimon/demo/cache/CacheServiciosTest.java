package unisimon.demo.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import unisimon.demo.config.CacheConfig;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.policy.AccesoInmueblePolicy;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.repository.ReglaReservaRepository;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.RompimientoRepository;
import unisimon.demo.repository.RompimientoRepository.RompimientoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.service.InmuebleService;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.PasarelaPagoService;
import unisimon.demo.service.ReglaReservaService;
import unisimon.demo.service.ReservaZonaComunService;
import unisimon.demo.service.ZonaComunService;
import unisimon.demo.service.impl.InmuebleServiceImpl;
import unisimon.demo.service.impl.LiquidacionServiceImpl;
import unisimon.demo.service.impl.PasarelaPagoServiceImpl;
import unisimon.demo.service.impl.ReglaReservaServiceImpl;
import unisimon.demo.service.impl.ReservaZonaComunServiceImpl;
import unisimon.demo.service.impl.ZonaComunServiceImpl;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los servicios reales detras del proxy de cache, con los repositorios simulados: una lectura repetida no vuelve
 * a la base de datos y cada escritura que cambia el dato invalida su cache.
 */
@SpringJUnitConfig(CacheServiciosTest.Contexto.class)
class CacheServiciosTest {

    @Configuration
    @Import(CacheConfig.class)
    static class Contexto {

        @Bean
        InmuebleRepository inmuebleRepository() {
            return mock(InmuebleRepository.class);
        }

        @Bean
        LiquidacionRepository liquidacionRepository() {
            return mock(LiquidacionRepository.class);
        }

        @Bean
        ZonaComunRepository zonaComunRepository() {
            return mock(ZonaComunRepository.class);
        }

        @Bean
        ReservaZonaComunRepository reservaRepository() {
            return mock(ReservaZonaComunRepository.class);
        }

        @Bean
        ReglaReservaRepository reglaRepository() {
            return mock(ReglaReservaRepository.class);
        }

        @Bean
        RompimientoRepository rompimientoRepository() {
            return mock(RompimientoRepository.class);
        }

        @Bean
        PasarelaPagoRepository pasarelaRepository() {
            return mock(PasarelaPagoRepository.class);
        }

        @Bean
        LiquidacionService liquidacionService(LiquidacionRepository liquidaciones, InmuebleRepository inmuebles) {
            return new LiquidacionServiceImpl(liquidaciones, inmuebles, mock(ParqueaderoRepository.class));
        }

        @Bean
        InmuebleService inmuebleService(InmuebleRepository inmuebles, ZonaComunRepository zonas,
                                        LiquidacionRepository liquidaciones) {
            return new InmuebleServiceImpl(inmuebles, mock(ParqueaderoRepository.class),
                    mock(UsuarioRepository.class), zonas, liquidaciones,
                    new EstadoPagoPolicy(Clock.systemDefaultZone()));
        }

        @Bean
        ZonaComunService zonaComunService(ZonaComunRepository zonas, InmuebleRepository inmuebles) {
            return new ZonaComunServiceImpl(zonas, inmuebles);
        }

        @Bean
        ReservaZonaComunService reservaService(ReservaZonaComunRepository reservas, ZonaComunRepository zonas) {
            return new ReservaZonaComunServiceImpl(reservas, zonas, mock(ResidenteRepository.class),
                    mock(NotificacionService.class), Clock.systemDefaultZone());
        }

        @Bean
        ReglaReservaService reglaService(ReglaReservaRepository reglas, RompimientoRepository rompimientos,
                                         ZonaComunRepository zonas) {
            return new ReglaReservaServiceImpl(reglas, rompimientos, zonas);
        }

        @Bean
        PasarelaPagoService pasarelaService(PasarelaPagoRepository pasarelas, LiquidacionService liquidaciones) {
            return new PasarelaPagoServiceImpl(pasarelas, mock(PagoRepository.class), mock(PagoService.class),
                    liquidaciones, mock(NotificacionService.class), mock(AccesoInmueblePolicy.class),
                    new unisimon.demo.bootstrap.PasarelaProperties("Wompi", "https://pasarela.test/", "pub_test_1234",
                            null, null, null, null, null),
                    mock(unisimon.demo.integration.wompi.ClienteWompi.class));
        }
    }

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private InmuebleRepository inmuebles;
    @Autowired
    private ZonaComunRepository zonas;
    @Autowired
    private ReservaZonaComunRepository reservas;
    @Autowired
    private ReglaReservaRepository reglas;
    @Autowired
    private RompimientoRepository rompimientos;
    @Autowired
    private PasarelaPagoRepository pasarelas;
    @Autowired
    private InmuebleService inmuebleService;
    @Autowired
    private LiquidacionService liquidacionService;
    @Autowired
    private ZonaComunService zonaService;
    @Autowired
    private ReservaZonaComunService reservaService;
    @Autowired
    private ReglaReservaService reglaService;
    @Autowired
    private PasarelaPagoService pasarelaService;

    @BeforeEach
    void limpiar() {
        reset(inmuebles, zonas, reservas, reglas, rompimientos, pasarelas);
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
    }

    @Test
    @DisplayName("el CacheManager crea exactamente los caches declarados en NombresCache")
    void cachesDeclarados() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(NombresCache.TODOS);
    }

    @Test
    @DisplayName("inmuebles: el listado se lee una vez y un cambio en las liquidaciones lo invalida")
    void inmuebles() {
        inmuebleService.listar();
        inmuebleService.listar();
        verify(inmuebles, times(1)).listarResumen();

        liquidacionService.marcarInmueblePagado(1L);
        inmuebleService.listar();
        verify(inmuebles, times(2)).listarResumen();
    }

    @Test
    @DisplayName("la invalidacion hecha dentro de una transaccion se aplica al confirmar, no antes")
    void invalidaAlConfirmar() {
        inmuebleService.listar();
        TransactionSynchronizationManager.initSynchronization();
        try {
            liquidacionService.marcarInmueblePagado(1L);
            inmuebleService.listar();
            verify(inmuebles, times(1)).listarResumen();   // la transaccion sigue abierta: aun vale el cache
            TransactionSynchronizationUtils.triggerAfterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        inmuebleService.listar();
        verify(inmuebles, times(2)).listarResumen();
    }

    @Test
    @DisplayName("zonas: se cachean por inmueble y cancelar una reserva las invalida (cambia reservasActuales)")
    void zonas() {
        zonaService.listar();
        zonaService.listar();
        zonaService.mostrarZonasComunes(1L);
        zonaService.mostrarZonasComunes(1L);
        zonaService.mostrarZonasComunes(2L);
        verify(zonas, times(1)).listar();
        verify(zonas, times(1)).listarActivasDeInmueble(1L);
        verify(zonas, times(1)).listarActivasDeInmueble(2L);

        when(reservas.buscarPorId(9L)).thenReturn(Optional.of(
                Proyecciones.de(ReservaView.class, "id", 9L, "duenoReservaId", 5L)));
        reservaService.cancelarReservaZonaComun(5L, 9L);

        zonaService.listar();
        zonaService.mostrarZonasComunes(1L);
        verify(zonas, times(2)).listar();
        verify(zonas, times(2)).listarActivasDeInmueble(1L);
    }

    @Test
    @DisplayName("reglas de reserva: el listado se lee una vez e inactivar una regla en una zona lo invalida")
    void reglas() {
        reglaService.listar();
        reglaService.listar();
        verify(reglas, times(1)).listar();

        when(rompimientos.buscarPorId(3L)).thenReturn(Optional.of(Proyecciones.de(RompimientoView.class,
                "id", 3L, "zonaComunId", 1L, "reglaReservaId", 2L, "activa", true)));
        reglaService.inactivarRegla(3L);

        reglaService.listar();
        verify(reglas, times(2)).listar();
    }

    @Test
    @DisplayName("pasarela: la configuracion se lee una vez y reconfigurarla la invalida")
    void pasarela() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(Proyecciones.de(PasarelaView.class,
                "id", 1L, "nombrePasarela", "Wompi", "urlApi", "https://pasarela.test/", "apiKey", "pub_test_1234",
                "estado", "ACTIVA")));
        pasarelaService.obtener();
        pasarelaService.obtener();
        verify(pasarelas, times(1)).buscarPrincipal();

        pasarelaService.configurar(new PagoRequests.ConfigurarPasarela("Wompi", "https://pasarela.test/",
                "pub_test_5678", "ACTIVA"));
        clearInvocations(pasarelas);
        pasarelaService.obtener();
        verify(pasarelas, times(1)).buscarPrincipal();
    }
}
