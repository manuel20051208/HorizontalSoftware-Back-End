package unisimon.demo.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.repository.PasarelaPagoRepository;

import java.util.List;

/**
 * Al arrancar deja la tabla pasarelas_pago igual al .env (nombre, URL del checkout y llave publica), conservando
 * el estado ACTIVA/INACTIVA que haya fijado el administrador. Asi el .env es la unica fuente de verdad y nunca
 * queda un valor viejo en la base de datos.
 * <p>
 * Tambien avisa en el log los problemas de configuracion (solo nombres de variables, nunca valores).
 * No detiene la aplicacion: el resto del sistema funciona y los pagos responden 409 hasta corregirlos.
 */
@Component
public class SincronizacionPasarela implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionPasarela.class);

    private final PasarelaProperties config;
    private final PasarelaPagoRepository pasarelas;

    public SincronizacionPasarela(PasarelaProperties config, PasarelaPagoRepository pasarelas) {
        this.config = config;
        this.pasarelas = pasarelas;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sincronizar();
        List<String> problemas = config.problemas();
        if (problemas.isEmpty()) {
            log.info("Pasarela de pagos '{}' lista en ambiente de {}", config.nombre().trim(), config.ambiente());
        } else {
            log.warn("Pasarela de pagos mal configurada; los pagos en linea y el webhook no funcionaran hasta "
                    + "corregir el .env: {}", String.join("; ", problemas));
        }
    }

    void sincronizar() {
        if (PasarelaProperties.vacio(config.nombre()) || PasarelaProperties.vacio(config.urlApi())
                || PasarelaProperties.vacio(config.apiKey())) {
            log.warn("No se sincroniza la pasarela: faltan PASARELA_NOMBRE, PASARELA_URL_API o PASARELA_API_KEY");
            return;
        }
        String nombre = config.nombre().trim();
        String urlApi = config.urlApi().trim();
        String apiKey = config.apiKey().trim();
        pasarelas.buscarPrincipal().ifPresentOrElse(
                actual -> {
                    boolean cambio = !nombre.equals(actual.getNombrePasarela()) || !urlApi.equals(actual.getUrlApi())
                            || !apiKey.equals(actual.getApiKey());
                    if (cambio) {
                        pasarelas.actualizar(actual.getId(), nombre, urlApi, apiKey, actual.getEstado());
                        log.info("Pasarela de pagos actualizada con la configuracion del entorno");
                    }
                },
                () -> {
                    pasarelas.insertar(new PasarelaPago(null, nombre, urlApi, apiKey));
                    log.info("Pasarela de pagos '{}' registrada con la configuracion del entorno", nombre);
                });
    }
}
