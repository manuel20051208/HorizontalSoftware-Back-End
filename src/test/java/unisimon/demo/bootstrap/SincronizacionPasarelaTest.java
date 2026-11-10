package unisimon.demo.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.support.Proyecciones;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SincronizacionPasarelaTest {

    @Mock
    private PasarelaPagoRepository pasarelas;

    private static PasarelaProperties config(String apiKey) {
        return new PasarelaProperties(" Wompi ", "https://checkout.wompi.co/p/", apiKey, "prod_integrity_a",
                "prod_events_a", "prv_prod_a", "https://production.wompi.co/v1", null);
    }

    private static PasarelaView enTabla(String apiKey, String estado) {
        return Proyecciones.de(PasarelaView.class, "id", 1L, "nombrePasarela", "Wompi",
                "urlApi", "https://checkout.wompi.co/p/", "apiKey", apiKey, "estado", estado);
    }

    @Test
    @DisplayName("si la tabla tiene una llave vieja la reemplaza por la del .env y conserva el estado")
    void actualizaLlaveVieja() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(enTabla("pub_test_CAMBIAR", "INACTIVA")));

        new SincronizacionPasarela(config("pub_prod_real"), pasarelas).sincronizar();

        verify(pasarelas).actualizar(1L, "Wompi", "https://checkout.wompi.co/p/", "pub_prod_real", "INACTIVA");
    }

    @Test
    @DisplayName("si la tabla ya coincide con el .env no escribe nada")
    void sinCambios() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.of(enTabla("pub_prod_real", "ACTIVA")));

        new SincronizacionPasarela(config("pub_prod_real"), pasarelas).sincronizar();

        verify(pasarelas, never()).actualizar(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("si no hay pasarela la registra desde el .env")
    void registra() {
        when(pasarelas.buscarPrincipal()).thenReturn(Optional.empty());

        new SincronizacionPasarela(config("pub_prod_real"), pasarelas).sincronizar();

        ArgumentCaptor<PasarelaPago> nueva = ArgumentCaptor.forClass(PasarelaPago.class);
        verify(pasarelas).insertar(nueva.capture());
        assertThat(nueva.getValue().getApiKey()).isEqualTo("pub_prod_real");
        assertThat(nueva.getValue().getNombrePasarela()).isEqualTo("Wompi");
    }

    @Test
    @DisplayName("sin llave publica en el .env no toca la tabla")
    void sinLlave() {
        new SincronizacionPasarela(config(""), pasarelas).sincronizar();

        verifyNoInteractions(pasarelas);
        verify(pasarelas, never()).insertar(any());
    }
}
