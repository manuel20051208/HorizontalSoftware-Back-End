package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.NoAutenticadoException;
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


/** Manejo de la contrasena, la cuenta y las preferencias por parte del propio usuario. */
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;
    private final GeneradorContrasena generador;
    private final CorreoSender correo;

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
        // Usuario.cambiarContrasena(inmueble, id): exige cuenta activa (y uno de sus inmuebles, si es residente).
        Usuario dominio = Hidratador.usuario(usuario);
        Inmueble inmueble = dominio instanceof Residente residente
                ? residente.getInmuebles().stream().findFirst().orElse(null) : null;
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
