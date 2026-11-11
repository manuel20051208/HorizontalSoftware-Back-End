package unisimon.demo.service.impl;

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
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.UsuarioGestionService;
import unisimon.demo.service.support.UsuarioPersistidor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Altas, consulta y bloqueo de cuentas por el administrador. */
@Service
public class UsuarioGestionServiceImpl implements UsuarioGestionService {

    private static final Set<String> TIPOS = Set.of(Usuario.ADMINISTRADOR, Usuario.RESIDENTE, Usuario.VIGILANTE,
            Usuario.USUARIO_AUTORIZADO);

    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final UsuarioFactory fabrica;
    private final UsuarioPersistidor persistidor;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;

    public UsuarioGestionServiceImpl(UsuarioRepository usuarios, InmuebleRepository inmuebles, UsuarioFactory fabrica,
                                     UsuarioPersistidor persistidor, PasswordEncoder codificador,
                                     PoliticaContrasena politica) {
        this.usuarios = usuarios;
        this.inmuebles = inmuebles;
        this.fabrica = fabrica;
        this.persistidor = persistidor;
        this.codificador = codificador;
        this.politica = politica;
    }

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
        return UsuarioMapper.de(usuarios.buscarCredencialesPorId(id).orElseThrow());
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
        CredencialesView usuario = usuarios.buscarCredencialesPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con esa identificacion"));
        if (Usuario.ADMINISTRADOR.equals(usuario.getTipoUsuario())) {
            throw new ProhibidoException("Un administrador no se puede activar ni desactivar desde aqui");
        }
        boolean nuevoEstado = !Boolean.TRUE.equals(usuario.getActive());
        usuarios.actualizarActive(usuario.getId(), nuevoEstado);
        if (!nuevoEstado) {
            usuarios.actualizarToken(usuario.getId(), null); // un usuario desactivado pierde su sesion
        }
        return UsuarioMapper.estadoActivo(usuario.getId(), usuario.getNombreCompleto(), nuevoEstado);
    }
}
