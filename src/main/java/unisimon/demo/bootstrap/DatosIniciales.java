package unisimon.demo.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.service.support.UsuarioPersistidor;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Al arrancar crea (solo si no existen) el administrador inicial y la pasarela de pagos, para que el sistema
 * se pueda usar desde el primer momento. Se apaga con app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final SeedProperties semilla;
    private final PasarelaProperties pasarela;
    private final UsuarioRepository usuarios;
    private final PasarelaPagoRepository pasarelas;
    private final UsuarioPersistidor persistidor;
    private final PasswordEncoder codificador;
    private final Clock reloj;

    public DatosIniciales(SeedProperties semilla, PasarelaProperties pasarela, UsuarioRepository usuarios,
                          PasarelaPagoRepository pasarelas, UsuarioPersistidor persistidor,
                          PasswordEncoder codificador, Clock reloj) {
        this.semilla = semilla;
        this.pasarela = pasarela;
        this.usuarios = usuarios;
        this.pasarelas = pasarelas;
        this.persistidor = persistidor;
        this.codificador = codificador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sembrarAdministrador();
        sembrarPasarela();
    }

    private void sembrarAdministrador() {
        SeedProperties.Admin datos = semilla.admin();
        if (usuarios.existePorCorreo(datos.correo()) || usuarios.existePorIdentificacion(datos.identificacion())) {
            return;
        }
        LocalDate hoy = LocalDate.now(reloj);
        Administrador admin = new Administrador(null, datos.identificacion(), datos.nombre(), null,
                datos.correo().toLowerCase(), codificador.encode(datos.contrasena()), Usuario.ADMINISTRADOR,
                hoy, hoy.plusYears(1));
        admin.setActive(true);
        persistidor.insertar(admin);
        log.warn("Administrador inicial creado ({}). Cambie su contrasena antes de usar el sistema en produccion.",
                datos.correo());
    }

    private void sembrarPasarela() {
        if (!pasarelas.existeAlguna()) {
            pasarelas.insertar(new PasarelaPago(null, pasarela.nombre(), pasarela.urlApi(), pasarela.apiKey()));
            log.info("Pasarela de pagos '{}' registrada con la configuracion inicial", pasarela.nombre());
        }
    }
}
