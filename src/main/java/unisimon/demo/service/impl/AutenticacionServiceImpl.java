package unisimon.demo.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.security.IdentidadToken;
import unisimon.demo.security.ParTokens;
import unisimon.demo.security.TokenService;
import unisimon.demo.service.AutenticacionService;

/**
 * Sesiones OAuth2: el access token (JWT corto) autentica cada peticion; el refresh token (JWT largo) solo sirve
 * para pedir un par nuevo. De cada refresh token se guarda unicamente su huella en usuarios.token: asi se puede
 * rotar (cada refresh invalida el anterior) y revocar (cerrar sesion).
 */
@Service
public class AutenticacionServiceImpl implements AutenticacionService {

    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final TokenService tokens;

    public AutenticacionServiceImpl(UsuarioRepository usuarios, PasswordEncoder codificador, TokenService tokens) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public AuthResponses.Sesion iniciarSesion(String correo, String contrasena) {
        CredencialesView usuario = usuarios.buscarCredencialesPorCorreo(correo.trim())
                .filter(u -> codificador.matches(contrasena, u.getContrasena()))
                .orElseThrow(() -> new NoAutenticadoException(CREDENCIALES_INVALIDAS));
        validarPuedeIniciarSesion(usuario);
        return abrirSesion(usuario);
    }

    /**
     * noRollbackFor: al detectar un refresh token reutilizado se revoca la sesion y se lanza el error; sin esto el
     * rollback desharia la revocacion.
     */
    @Override
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public AuthResponses.Sesion renovarSesion(String refreshToken) {
        IdentidadToken identidad = tokens.leerRefresh(refreshToken);
        CredencialesView usuario = usuarios.buscarCredencialesPorId(identidad.usuarioId())
                .orElseThrow(() -> new NoAutenticadoException("Sesion invalida"));
        if (!tokens.huella(refreshToken).equals(usuario.getToken())) {
            // Un refresh token ya rotado o revocado: se corta la sesion por seguridad.
            usuarios.actualizarToken(usuario.getId(), null);
            throw new NoAutenticadoException("El refresh token ya fue usado o fue revocado; inicie sesion de nuevo");
        }
        validarPuedeIniciarSesion(usuario);
        return abrirSesion(usuario);
    }

    @Override
    @Transactional
    public void cerrarSesion(Long usuarioId) {
        usuarios.actualizarToken(usuarioId, null);
    }

    private void validarPuedeIniciarSesion(CredencialesView usuario) {
        if (Usuario.USUARIO_AUTORIZADO.equals(usuario.getTipoUsuario())) {
            throw new ProhibidoException("Los invitados no inician sesion en el sistema");
        }
        if (!Boolean.TRUE.equals(usuario.getActive())) {
            throw new ProhibidoException("La cuenta no esta activa: activela o pida autorizacion a la administracion");
        }
    }

    private AuthResponses.Sesion abrirSesion(CredencialesView usuario) {
        ParTokens par = tokens.emitir(
                new IdentidadToken(usuario.getId(), usuario.getTipoUsuario(), usuario.getNombreCompleto()));
        usuarios.actualizarToken(usuario.getId(), tokens.huella(par.refreshToken()));
        return UsuarioMapper.sesion(par, UsuarioMapper.de(usuario));
    }
}
