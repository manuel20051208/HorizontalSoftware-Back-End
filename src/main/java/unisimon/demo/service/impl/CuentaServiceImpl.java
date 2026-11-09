package unisimon.demo.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.CuentaService;

import java.time.Clock;
import java.time.LocalDate;

/** Activacion de cuenta y manejo de contrasenas por parte del propio usuario. */
@Service
public class CuentaServiceImpl implements CuentaService {

    /** Dias, desde que el administrador crea la cuenta, durante los cuales el usuario puede activarla solo. */
    static final int DIAS_VENTANA_ACTIVACION = 7;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;
    private final GeneradorContrasena generador;
    private final CorreoSender correo;
    private final Clock reloj;

    public CuentaServiceImpl(UsuarioRepository usuarios, PasswordEncoder codificador, PoliticaContrasena politica,
                             GeneradorContrasena generador, CorreoSender correo, Clock reloj) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.politica = politica;
        this.generador = generador;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public UsuarioResponses.Activacion activarCuenta(String correoElectronico, String contrasena) {
        CredencialesView usuario = usuarios.buscarCredencialesPorCorreo(correoElectronico.trim())
                .filter(u -> codificador.matches(contrasena, u.getContrasena()))
                .orElseThrow(() -> new NoAutenticadoException("Correo o contrasena incorrectos"));
        if (Boolean.TRUE.equals(usuario.getActive())) {
            return new UsuarioResponses.Activacion(true, "La cuenta ya estaba activa");
        }
        if (Usuario.USUARIO_AUTORIZADO.equals(usuario.getTipoUsuario())) {
            throw new ProhibidoException("Los invitados no tienen cuenta que activar");
        }
        LocalDate ultimoDia = usuario.getFechaCreacionCuenta().plusDays(DIAS_VENTANA_ACTIVACION);
        if (LocalDate.now(reloj).isAfter(ultimoDia)) {
            throw new ProhibidoException("El plazo de activacion vencio: pida a la administracion que active su cuenta");
        }
        usuarios.actualizarActive(usuario.getId(), true);
        return new UsuarioResponses.Activacion(true, "Cuenta activada");
    }

    @Override
    @Transactional
    public void recuperarContrasena(String correoElectronico) {
        // Siempre responde igual exista o no el correo: no se revela quien tiene cuenta.
        usuarios.buscarCredencialesPorCorreo(correoElectronico.trim())
                .filter(u -> !Usuario.USUARIO_AUTORIZADO.equals(u.getTipoUsuario()))
                .ifPresent(u -> {
                    String temporal = generador.generar();
                    usuarios.actualizarContrasena(u.getId(), codificador.encode(temporal));
                    usuarios.actualizarToken(u.getId(), null);
                    correo.enviar(u.getCorreoElectronico(), "Recuperacion de contrasena - Torres del Parque",
                            "Su contrasena temporal es: " + temporal + ". Cambiela apenas inicie sesion.");
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponses.Usuario consultarCuenta(Long usuarioId) {
        return usuarios.buscarCredencialesPorId(usuarioId).map(UsuarioMapper::de)
                .orElseThrow(() -> new NoAutenticadoException("Sesion invalida"));
    }

    @Override
    @Transactional
    public void cambiarContrasena(Long usuarioId, String contrasenaActual, String contrasenaNueva) {
        CredencialesView usuario = usuarios.buscarCredencialesPorId(usuarioId)
                .orElseThrow(() -> new NoAutenticadoException("Sesion invalida"));
        if (!codificador.matches(contrasenaActual, usuario.getContrasena())) {
            throw new NoAutenticadoException("La contrasena actual es incorrecta");
        }
        politica.validar(contrasenaNueva);
        usuarios.actualizarContrasena(usuarioId, codificador.encode(contrasenaNueva));
        usuarios.actualizarToken(usuarioId, null);
    }
}
