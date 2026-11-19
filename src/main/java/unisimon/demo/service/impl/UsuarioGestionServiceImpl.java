package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.factory.UsuarioFactory;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.CodigoVerificacionService;
import unisimon.demo.service.UsuarioGestionService;
import unisimon.demo.service.support.UsuarioPersistidor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Altas, verificacion del correo, consulta y bloqueo de cuentas por el administrador. Una cuenta nueva queda
 * inactiva hasta que el administrador confirma el codigo de 4 cifras que llego a su correo.
 */
@Service
@RequiredArgsConstructor
public class UsuarioGestionServiceImpl implements UsuarioGestionService {

    private static final Set<String> TIPOS = Set.of(Usuario.ADMINISTRADOR, Usuario.RESIDENTE, Usuario.VIGILANTE,
            Usuario.USUARIO_AUTORIZADO);

    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final UsuarioFactory fabrica;
    private final UsuarioPersistidor persistidor;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;
    private final CodigoVerificacionService codigos;

    @Override
    @Transactional
    public UsuarioResponses.Usuario gestionarCuentaUsuario(UsuarioRequests.CrearUsuario s) {
        if (usuarios.existePorCorreo(s.correoElectronico().trim())) {
            throw new ConflictoException("Ya existe una cuenta con ese correo");
        }
        if (usuarios.existePorIdentificacion(s.identificacion())) {
            throw new ConflictoException("Ya existe una cuenta con esa identificacion");
        }
        politica.validar(s.contrasena());
        if (s.inmuebleId() != null && !inmuebles.existePorId(s.inmuebleId())) {
            throw new RecursoNoEncontradoException("El inmueble " + s.inmuebleId() + " no existe");
        }
        Usuario nuevo = fabrica.crear(s, codificador.encode(s.contrasena()));
        Long id = persistidor.insertar(nuevo);
        codigos.emitir(id, nuevo.getCorreoElectronico());   // queda inactiva hasta verificar el correo
        return UsuarioMapper.de(usuarios.buscarCredencialesPorId(id).orElseThrow());
    }

    @Override
    @Transactional
    public UsuarioResponses.Usuario verificarCorreo(Long identificacion, String codigo) {
        CredencialesView usuario = pendienteDeVerificar(identificacion);
        codigos.verificar(usuario.getId(), codigo);   // REQUIRES_NEW: un intento fallido queda guardado
        // Usuario.activarCuenta(correo, contrasena): con las credenciales guardadas (contrasena ya codificada).
        if (!Hidratador.usuario(usuario).activarCuenta(usuario.getCorreoElectronico(), usuario.getContrasena())) {
            throw new ConflictoException("La cuenta ya estaba activa");
        }
        usuarios.marcarCorreoVerificado(usuario.getId());
        usuarios.actualizarActive(usuario.getId(), true);
        return UsuarioMapper.de(usuarios.buscarCredencialesPorId(usuario.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public String reenviarCodigo(Long identificacion) {
        CredencialesView usuario = pendienteDeVerificar(identificacion);
        codigos.emitir(usuario.getId(), usuario.getCorreoElectronico());
        return usuario.getCorreoElectronico();
    }

    /** La cuenta existe, no es de un invitado y su correo aun no se ha verificado. */
    private CredencialesView pendienteDeVerificar(Long identificacion) {
        CredencialesView usuario = buscarPorIdentificacion(identificacion);
        if (Usuario.USUARIO_AUTORIZADO.equals(usuario.getTipoUsuario())) {
            throw new ProhibidoException("Los invitados no tienen cuenta que verificar");
        }
        if (Boolean.TRUE.equals(usuario.getCorreoVerificado())) {
            throw new ConflictoException("El correo de esta cuenta ya esta verificado");
        }
        return usuario;
    }

    private CredencialesView buscarPorIdentificacion(Long identificacion) {
        return usuarios.buscarCredencialesPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con esa identificacion"));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<UsuarioResponses.Usuario> listar(String tipoUsuario, Boolean active, SolicitudPagina pagina) {
        String tipo = null;
        if (tipoUsuario != null && !tipoUsuario.isBlank()) {
            tipo = tipoUsuario.trim().toUpperCase(Locale.ROOT);
            if (!TIPOS.contains(tipo)) {
                throw new SolicitudInvalidaException("tipoUsuario no es valido: " + tipoUsuario);
            }
        }
        List<UsuarioResponses.Usuario> filas = usuarios.listar(tipo, active, pagina.limite(), pagina.desplazamiento())
                .stream().map(UsuarioMapper::de).toList();
        return Pagina.de(filas, usuarios.contar(tipo, active), pagina);
    }

    @Override
    @Transactional
    public UsuarioResponses.EstadoActivo modificarActive(Long identificacion) {
        CredencialesView usuario = buscarPorIdentificacion(identificacion);
        if (Usuario.ADMINISTRADOR.equals(usuario.getTipoUsuario())) {
            throw new ProhibidoException("Un administrador no se puede activar ni desactivar desde aqui");
        }
        boolean nuevoEstado = !Boolean.TRUE.equals(usuario.getActive());
        boolean esInvitado = Usuario.USUARIO_AUTORIZADO.equals(usuario.getTipoUsuario());
        if (nuevoEstado && !esInvitado && !Boolean.TRUE.equals(usuario.getCorreoVerificado())) {
            throw new ConflictoException("Primero verifique el correo con el codigo que se le envio");
        }
        usuarios.actualizarActive(usuario.getId(), nuevoEstado);
        if (!nuevoEstado) {
            usuarios.actualizarToken(usuario.getId(), null); // un usuario desactivado pierde su sesion
        }
        return UsuarioMapper.estadoActivo(usuario.getId(), usuario.getNombreCompleto(), nuevoEstado);
    }
}
