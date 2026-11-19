package unisimon.demo.bootstrap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.service.support.UsuarioPersistidor;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Al arrancar crea (solo si no existe) el administrador inicial, para que el sistema se pueda usar desde el
 * primer momento. Se apaga con app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final SeedProperties semilla;
    private final UsuarioRepository usuarios;
    private final UsuarioPersistidor persistidor;
    private final PasswordEncoder codificador;
    private final Clock reloj;

    /** La pasarela ya no se siembra aqui: la sincroniza {@link SincronizacionPasarela} con el .env en cada arranque. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sembrarAdministrador();
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
        Long id = persistidor.insertar(admin);
        usuarios.marcarCorreoVerificado(id);   // lo crea el sistema: no hay administrador que le confirme un codigo
        log.warn("Administrador inicial creado ({}). Cambie su contrasena antes de usar el sistema en produccion.",
                datos.correo());
    }
}
