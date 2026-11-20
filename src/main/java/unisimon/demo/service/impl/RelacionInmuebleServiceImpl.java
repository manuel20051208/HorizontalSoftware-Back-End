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
import java.util.ArrayList;
import java.util.List;

/**
 * Relacion Residente/Invitado <> Inmueble en la tabla puente {@code usuario_inmuebles}. Residentes e invitados
 * pueden tener varios inmuebles; admin y vigilante no se relacionan. Quitar un inmueble arrastra, en la misma
 * transaccion, a los invitados, el parqueadero y las reservas futuras. Toda operacion la autoriza el
 * administrador con {@link Administrador#gestionarCuentaUsuario()}.
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
    public UsuarioResponses.UsuarioInmuebles consultar(Long adminId, Long identificacion) {
        administrador(adminId);
        return respuesta(buscarCuenta(identificacion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponses.UsuarioDeInmueble> listarUsuariosDeInmueble(Long adminId, Long inmuebleId) {
        administrador(adminId);
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
        return usuarios.listarUsuariosDeInmueble(inmuebleId).stream()
                .map(UsuarioMapper::usuarioDeInmueble).toList();
    }

    @Override
    @Transactional
    public UsuarioResponses.UsuarioInmuebles vincular(Long adminId, Long identificacion, Long inmuebleId) {
        administrador(adminId);
        CredencialesView cuenta = buscarCuenta(identificacion);
        inmuebleDisponible(inmuebleId);
        if (usuarios.estaRelacionado(cuenta.getId(), inmuebleId)) {
            throw new ConflictoException("La cuenta ya esta relacionada con ese inmueble");
        }
        usuarios.vincularInmueble(cuenta.getId(), inmuebleId);   // no reactiva ni verifica el correo
        return respuesta(cuenta);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public UsuarioResponses.UsuarioInmuebles mudar(Long adminId, Long identificacion, Long inmuebleOrigenId,
                                                   Long inmuebleDestinoId) {
        Administrador admin = administrador(adminId);
        CredencialesView cuenta = buscarCuenta(identificacion);
        if (!usuarios.estaRelacionado(cuenta.getId(), inmuebleOrigenId)) {
            throw new ConflictoException("La cuenta no estaba relacionada con el inmueble de origen");
        }
        if (usuarios.estaRelacionado(cuenta.getId(), inmuebleDestinoId)) {
            throw new ConflictoException("La cuenta ya esta relacionada con el inmueble de destino");
        }
        inmuebleDisponible(inmuebleDestinoId);
        quitarDeInmueble(admin, cuenta, inmuebleOrigenId, inmuebleDestinoId);
        usuarios.vincularInmueble(cuenta.getId(), inmuebleDestinoId);
        return respuesta(cuenta);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public void desvincular(Long adminId, Long identificacion, Long inmuebleId) {
        Administrador admin = administrador(adminId);
        CredencialesView cuenta = buscarCuenta(identificacion);
        if (!usuarios.estaRelacionado(cuenta.getId(), inmuebleId)) {
            throw new ConflictoException("La cuenta no estaba relacionada con ese inmueble");
        }
        quitarDeInmueble(admin, cuenta, inmuebleId, null);
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

    /** Solo residentes e invitados se relacionan con inmuebles. */
    private CredencialesView buscarCuenta(Long identificacion) {
        CredencialesView cuenta = usuarios.buscarCredencialesPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con esa identificacion"));
        if (!Usuario.RESIDENTE.equals(cuenta.getTipoUsuario())
                && !Usuario.USUARIO_AUTORIZADO.equals(cuenta.getTipoUsuario())) {
            throw new ConflictoException("Solo residentes e invitados se relacionan con inmuebles");
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

    /**
     * Reglas de la salida de X: invitados, parqueadero, reservas y desactivacion si la cuenta se queda sin
     * inmuebles. En mudanza los invitados y las reservas ganan Y, asi que nadie se desactiva. Solo se tocan los
     * invitados que compartian X con el residente: un invitado ligado solo a otro inmueble Z del residente no
     * pierde X (que no tenia) ni gana Y (que no le corresponde).
     */
    private void quitarDeInmueble(Administrador admin, CredencialesView cuenta, Long origenId, Long destinoId) {
        Long usuarioId = cuenta.getId();
        List<Long> invitadosAfectados = new ArrayList<>();
        if (Usuario.RESIDENTE.equals(cuenta.getTipoUsuario())) {
            List<Long> invitadosDelOrigen = invitados.listarIdsDeResidenteEnInmueble(usuarioId, origenId);
            for (Long invitadoId : invitadosDelOrigen) {
                if (!invitados.tieneOtroAnfitrionEnInmueble(invitadoId, usuarioId, origenId)) {
                    invitadosAfectados.add(invitadoId);
                }
                if (destinoId != null) {
                    invitados.vincularAInmueble(List.of(invitadoId), destinoId);
                }
            }
            if (!invitadosAfectados.isEmpty()) {
                invitados.desvincularDeInmueble(invitadosAfectados, origenId);
            }
            autos.liberarParqueaderoDeInmueble(usuarioId, origenId);
            if (destinoId != null) {
                reservas.moverFuturasAInmueble(usuarioId, origenId, destinoId, ahora());
            }
            reservas.cancelarFuturasDeInmueble(usuarioId, origenId, ahora());
        }
        usuarios.desvincularInmueble(usuarioId, origenId);
        if (destinoId == null) {
            desactivarSiSinInmuebles(admin, cuenta);
            for (Long invitadoId : invitadosAfectados) {
                usuarios.buscarCredencialesPorId(invitadoId).ifPresent(invitado ->
                        desactivarSiSinInmuebles(admin, invitado));
            }
        }
    }

    /** La cuenta activa que se queda sin inmuebles se desactiva con la operacion del modelo y pierde su sesion. */
    private void desactivarSiSinInmuebles(Administrador admin, CredencialesView cuenta) {
        if (!Boolean.TRUE.equals(cuenta.getActive()) || usuarios.contarInmueblesDe(cuenta.getId()) > 0) {
            return;
        }
        Usuario dominio = Hidratador.usuario(cuenta);
        admin.modificarActive(cuenta.getIdentificacion(), admin.getId(), List.of(dominio));
        usuarios.actualizarActive(cuenta.getId(), false);
        usuarios.actualizarToken(cuenta.getId(), dominio.getToken());   // cerrarSesion() lo dejo en null
    }

    private UsuarioResponses.UsuarioInmuebles respuesta(CredencialesView cuenta) {
        return UsuarioMapper.usuarioInmuebles(cuenta, usuarios.listarInmueblesDe(cuenta.getId()));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
