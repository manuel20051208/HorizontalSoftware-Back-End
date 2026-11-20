package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.InvitadoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.InvitadoMapper;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.service.InvitadoService;
import unisimon.demo.service.support.InmuebleDelResidente;
import unisimon.demo.service.support.UsuarioPersistidor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Invitados de un residente. Un invitado no inicia sesion: su clave es aleatoria e inutilizable y solo existe
 * porque la tabla de usuarios la exige. El residente decide si esta activo.
 */
@Service
@RequiredArgsConstructor
public class InvitadoServiceImpl implements InvitadoService {

    private final InvitadoRepository invitados;
    private final UsuarioRepository usuarios;
    private final InmuebleDelResidente inmuebleDelResidente;
    private final UsuarioPersistidor persistidor;
    private final PasswordEncoder codificador;
    private final GeneradorContrasena generador;
    private final Clock reloj;

    @Override
    @Transactional
    public InvitadoResponses.Invitado crearInvitado(Long residenteId, Long inmuebleId,
                                                    InvitadoRequests.CrearInvitado s) {
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        if (s.venceAutorizacion() != null && s.venceAutorizacion().isBefore(LocalDate.now(reloj))) {
            throw new SolicitudInvalidaException("venceAutorizacion no puede estar en el pasado");
        }
        if (usuarios.existePorIdentificacion(s.identificacion())) {
            throw new ConflictoException("Ya existe una persona registrada con esa identificacion");
        }
        String correo = s.correoElectronico() == null || s.correoElectronico().isBlank()
                ? "invitado-" + s.identificacion() + "@invitados.torresdelparque.local"
                : s.correoElectronico().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existePorCorreo(correo)) {
            throw new ConflictoException("Ya existe una cuenta con ese correo");
        }
        UsuarioAutorizado invitado = new UsuarioAutorizado(null, s.identificacion(), s.nombreCompleto().trim(),
                s.fechaNacimiento(), correo, codificador.encode(generador.generar()), Usuario.USUARIO_AUTORIZADO,
                List.of(Hidratador.referenciaInmueble(elegido)), null, s.venceAutorizacion());
        invitado.setActive(true);   // lo autoriza el propio residente
        Long id = persistidor.insertar(invitado, elegido);   // el invitado tambien se vincula al inmueble
        invitados.vincular(residenteId, id);
        return InvitadoMapper.invitado(invitados.buscarPorId(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<InvitadoResponses.Invitado> listarInvitados(Long residenteId, Long inmuebleId,
                                                              SolicitudPagina pagina) {
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        List<InvitadoResponses.Invitado> filas = invitados.listarDeResidenteEnInmueble(residenteId, elegido,
                pagina.limite(), pagina.desplazamiento()).stream().map(InvitadoMapper::invitado).toList();
        return Pagina.de(filas, invitados.contarDeResidenteEnInmueble(residenteId, elegido), pagina);
    }

    @Override
    @Transactional
    public InvitadoResponses.CambioEstado cambiarEstadoInvitado(Long residenteId, Long invitadoId) {
        InvitadoView invitado = propio(residenteId, invitadoId);
        boolean nuevoEstado = !Boolean.TRUE.equals(invitado.getActive());
        usuarios.actualizarActive(invitadoId, nuevoEstado);
        return new InvitadoResponses.CambioEstado(invitadoId, nuevoEstado);
    }

    @Override
    @Transactional
    public InvitadoResponses.CambioEstadoMasivo cambiarEstadoInvitados(Long residenteId, List<Long> invitadoIds) {
        List<Long> ids = invitadoIds.stream().distinct().toList();
        List<InvitadoView> propios = ids.stream().map(id -> propio(residenteId, id)).toList();   // todos o ninguno
        List<InvitadoResponses.CambioEstado> cambios = propios.stream().map(invitado -> {
            boolean nuevoEstado = !Boolean.TRUE.equals(invitado.getActive());
            usuarios.actualizarActive(invitado.getId(), nuevoEstado);
            return new InvitadoResponses.CambioEstado(invitado.getId(), nuevoEstado);
        }).toList();
        return new InvitadoResponses.CambioEstadoMasivo(cambios);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<InvitadoResponses.Anfitrion> buscarRelacionResidente(Long invitadoId, SolicitudPagina pagina) {
        invitados.buscarPorId(invitadoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El invitado " + invitadoId + " no existe"));
        List<InvitadoResponses.Anfitrion> filas = invitados.listarAnfitriones(invitadoId, pagina.limite(),
                pagina.desplazamiento()).stream().map(InvitadoMapper::anfitrion).toList();
        return Pagina.de(filas, invitados.contarAnfitriones(invitadoId), pagina);
    }

    private InvitadoView propio(Long residenteId, Long invitadoId) {
        InvitadoView invitado = invitados.buscarPorId(invitadoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El invitado " + invitadoId + " no existe"));
        if (!invitados.esInvitadoDe(residenteId, invitadoId)) {
            throw new ProhibidoException("El invitado " + invitadoId + " no fue autorizado por usted");
        }
        return invitado;
    }

    @Override
    @Transactional(readOnly = true)
    public InvitadoResponses.Invitado consultarInvitado(Long residenteId, Long invitadoId) {
        return InvitadoMapper.invitado(propio(residenteId, invitadoId));
    }

    @Override
    @Transactional
    public InvitadoResponses.Invitado actualizarInvitado(Long residenteId, Long invitadoId,
                                                         InvitadoRequests.ActualizarInvitado s) {
        propio(residenteId, invitadoId);
        if (s.venceAutorizacion() != null && s.venceAutorizacion().isBefore(LocalDate.now(reloj))) {
            throw new SolicitudInvalidaException("venceAutorizacion no puede estar en el pasado");
        }
        invitados.actualizarDatos(invitadoId, s.nombreCompleto().trim(), s.fechaNacimiento(), s.venceAutorizacion());
        return InvitadoMapper.invitado(invitados.buscarPorId(invitadoId).orElseThrow());
    }

    /**
     * Residente ◇— UsuarioAutorizado es agregacion: el residente quita SU vinculo y el invitado sigue existiendo.
     * Si ya ningun residente lo autoriza, queda inactivo (no puede entrar).
     */
    @Override
    @Transactional
    public void eliminarInvitado(Long residenteId, Long invitadoId) {
        propio(residenteId, invitadoId);
        invitados.desvincular(residenteId, invitadoId);
        if (invitados.contarAnfitriones(invitadoId) == 0) {
            usuarios.actualizarActive(invitadoId, false);
        }
    }
}
