package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.model.Administrador;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.RelacionInmuebleService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Relacion Residente <> Inmueble. Solo las cuentas de residente se relacionan con un inmueble; los invitados
 * siguen a su residente. Cambiar o desvincular arrastra, en la misma transaccion, a los invitados exclusivos, el
 * parqueadero del auto y las reservas futuras. Toda operacion la autoriza el administrador que la pide con
 * {@link Administrador#gestionarCuentaUsuario()}.
 */
@Service
@RequiredArgsConstructor
public class RelacionInmuebleServiceImpl implements RelacionInmuebleService {

    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final InvitadoRepository invitados;
    private final AutoRepository autos;
    private final ReservaZonaComunRepository reservas;
    private final Clock reloj;

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponses.RelacionInmueble consultar(Long adminId, Long identificacion) {
        administrador(adminId);
        CredencialesView residente = buscarResidente(identificacion);
        InmuebleBasicoView inmueble = residente.getInmuebleId() == null ? null
                : inmuebles.buscarBasico(residente.getInmuebleId()).orElse(null);
        return relacion(residente, inmueble);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponses.RelacionInmueble> listarPorInmueble(Long adminId, Long inmuebleId) {
        administrador(adminId);
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
        return usuarios.listarResidentesDeInmueble(inmuebleId).stream()
                .map(UsuarioMapper::relacionInmueble).toList();
    }

    @Override
    @Transactional
    public UsuarioResponses.RelacionInmueble vincular(Long adminId, Long identificacion, Long inmuebleId) {
        administrador(adminId);
        CredencialesView residente = buscarResidente(identificacion);
        if (residente.getInmuebleId() != null) {
            throw new ConflictoException("La cuenta ya tiene un inmueble; use cambiar");
        }
        InmuebleBasicoView inmueble = inmuebleDisponible(inmuebleId);
        usuarios.actualizarInmueble(residente.getId(), inmuebleId);   // la cuenta sigue inactiva hasta verificar
        return relacion(residente, inmueble);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public UsuarioResponses.RelacionInmueble cambiar(Long adminId, Long identificacion, Long inmuebleId) {
        administrador(adminId);
        CredencialesView residente = buscarResidente(identificacion);
        if (residente.getInmuebleId() == null) {
            throw new ConflictoException("La cuenta no tiene un inmueble; use vincular");
        }
        if (residente.getInmuebleId().equals(inmuebleId)) {
            throw new ConflictoException("La cuenta ya esta relacionada con ese inmueble");
        }
        InmuebleBasicoView inmueble = inmuebleDisponible(inmuebleId);
        mudarInvitados(residente.getId(), inmuebleId);
        autos.liberarParqueaderoDe(residente.getId());
        reservas.cancelarProgramadasFuturas(residente.getId(), inmuebleId, ahora());
        usuarios.actualizarInmueble(residente.getId(), inmuebleId);
        return relacion(residente, inmueble);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public void desvincular(Long adminId, Long identificacion) {
        Administrador admin = administrador(adminId);
        CredencialesView residente = buscarResidente(identificacion);
        if (residente.getInmuebleId() == null) {
            throw new ConflictoException("La cuenta no tiene un inmueble relacionado");
        }
        List<Long> exclusivos = invitados.listarExclusivosDeResidente(residente.getId());
        if (!exclusivos.isEmpty()) {
            invitados.desactivar(exclusivos);   // conservan el inmueble viejo
        }
        invitados.borrarVinculosDeResidente(residente.getId());   // exclusivos y compartidos
        autos.liberarParqueaderoDe(residente.getId());
        reservas.cancelarProgramadasFuturas(residente.getId(), null, ahora());
        if (Boolean.TRUE.equals(residente.getActive())) {
            Usuario cuenta = Hidratador.usuario(residente);
            admin.modificarActive(identificacion, adminId, List.of(cuenta));   // la deja inactiva
            usuarios.actualizarActive(residente.getId(), false);
            usuarios.actualizarToken(residente.getId(), cuenta.getToken());   // cerrarSesion() lo dejo en null
        }
        usuarios.actualizarInmueble(residente.getId(), null);
    }

    // ------------------------------------------------------------------ apoyo

    /** El administrador que hace la peticion tiene que serlo y estar vigente. */
    private Administrador administrador(Long adminId) {
        Usuario cuenta = Hidratador.usuario(usuarios.buscarCredencialesPorId(adminId)
                .orElseThrow(() -> new ProhibidoException("Solo un administrador puede gestionar esta relacion")));
        if (!(cuenta instanceof Administrador administrador)) {
            throw new ProhibidoException("Solo un administrador puede gestionar esta relacion");
        }
        administrador.gestionarCuentaUsuario();   // IllegalStateException -> 409 en GlobalExceptionHandler
        return administrador;
    }

    /** Solo las cuentas de residente se relacionan con un inmueble; un invitado sigue a su residente. */
    private CredencialesView buscarResidente(Long identificacion) {
        CredencialesView cuenta = usuarios.buscarCredencialesPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con esa identificacion"));
        if (Usuario.USUARIO_AUTORIZADO.equals(cuenta.getTipoUsuario())) {
            throw new ProhibidoException("Los invitados siguen a su residente: no se relacionan con un inmueble");
        }
        if (!Usuario.RESIDENTE.equals(cuenta.getTipoUsuario())) {
            throw new ConflictoException("Solo los residentes se relacionan con un inmueble");
        }
        return cuenta;
    }

    private InmuebleBasicoView inmuebleDisponible(Long inmuebleId) {
        InmuebleBasicoView inmueble = inmuebles.buscarBasico(inmuebleId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe"));
        if (!Boolean.TRUE.equals(inmueble.getIsActive())) {
            throw new ConflictoException("El inmueble " + inmueble.getDireccionInmueble() + " esta inactivo");
        }
        return inmueble;
    }

    /** Los invitados exclusivos se mudan; los compartidos pierden solo el vinculo con este residente. */
    private void mudarInvitados(Long residenteId, Long inmuebleId) {
        List<Long> exclusivos = invitados.listarExclusivosDeResidente(residenteId);
        for (Long invitadoId : invitados.listarIdsDeResidente(residenteId)) {
            if (!exclusivos.contains(invitadoId)) {
                invitados.borrarVinculo(residenteId, invitadoId);
            }
        }
        if (!exclusivos.isEmpty()) {
            invitados.moverAInmueble(exclusivos, inmuebleId);
        }
    }

    private UsuarioResponses.RelacionInmueble relacion(CredencialesView residente, InmuebleBasicoView inmueble) {
        return UsuarioMapper.relacionInmueble(residente, inmueble == null ? null : inmueble.getId(),
                inmueble == null ? null : inmueble.getDireccionInmueble());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
