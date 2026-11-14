package unisimon.demo.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.PreferenciasView;
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
        // Usuario.activarCuenta(correo, contrasena): la contrasena ya quedo verificada arriba con el codificador.
        if (!Hidratador.usuario(usuario).activarCuenta(correoElectronico, usuario.getContrasena())) {
            throw new NoAutenticadoException("Correo o contrasena incorrectos");
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
                    Usuario dominio = Hidratador.usuario(u);
                    dominio.recuperarContrasena();                       // Usuario.recuperarContrasena(): cierra la sesion
                    String temporal = generador.generar();
                    usuarios.actualizarContrasena(u.getId(), codificador.encode(temporal));
                    usuarios.actualizarToken(u.getId(), dominio.getToken());
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
        // Usuario.cambiarContrasena(inmueble, id): exige cuenta activa (y su inmueble, si es residente) y cierra la sesion.
        Usuario dominio = Hidratador.usuario(usuario);
        Inmueble inmueble = dominio instanceof Residente residente ? residente.getInmueble() : null;
        dominio.cambiarContrasena(inmueble, usuarioId);
        usuarios.actualizarContrasena(usuarioId, codificador.encode(contrasenaNueva));
        usuarios.actualizarToken(usuarioId, dominio.getToken());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionResponses.Preferencias consultarPreferencias(Long usuarioId) {
        return new NotificacionResponses.Preferencias(cargarPreferencias(usuarioId).getPreferenciasNotificacion());
    }

    @Override
    @Transactional
    public NotificacionResponses.Preferencias configurarPreferencias(Long usuarioId, String tipoNotificacion) {
        Usuario usuario = cargarPreferencias(usuarioId);
        usuario.configurarPreferencias(tipoNotificacion);   // falla si la cuenta esta inactiva (isActive)
        usuarios.actualizarPreferencias(usuarioId, String.join(",", usuario.getPreferenciasNotificacion()));
        return new NotificacionResponses.Preferencias(usuario.getPreferenciasNotificacion());
    }

    private Usuario cargarPreferencias(Long usuarioId) {
        PreferenciasView vista = usuarios.buscarPreferencias(usuarioId)
                .orElseThrow(() -> new NoAutenticadoException("Sesion invalida"));
        return Hidratador.usuarioConPreferencias(usuarioId, vista);
    }
}
