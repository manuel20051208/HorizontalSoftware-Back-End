package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.NotificacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.NotificacionMapper;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.Notificacion;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.ZonaComun;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.NotificacionRepository;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.service.NotificacionService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Notificaciones. El dominio ({@link Notificacion}) arma destinatarios y mensaje; aqui se resuelven los datos
 * necesarios y se guarda el resultado.
 */
@Service
public class NotificacionServiceImpl implements NotificacionService {

    static final String TIPO_GENERAL = "GENERAL";

    private final NotificacionRepository notificaciones;
    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final LiquidacionRepository liquidaciones;
    private final ReservaZonaComunRepository reservas;

    public NotificacionServiceImpl(NotificacionRepository notificaciones, UsuarioRepository usuarios,
                                   InmuebleRepository inmuebles, LiquidacionRepository liquidaciones,
                                   ReservaZonaComunRepository reservas) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        this.inmuebles = inmuebles;
        this.liquidaciones = liquidaciones;
        this.reservas = reservas;
    }

    @Override
    @Transactional
    public NotificacionResponses.Notificacion enviarNotificacion(NotificacionRequests.Enviar s) {
        List<Usuario> destinatarios;
        if (s.usuarioIds() != null && !s.usuarioIds().isEmpty()) {
            destinatarios = s.usuarioIds().stream().distinct().map(Hidratador::referenciaUsuario).toList();
        } else if (s.inmuebleId() != null) {
            destinatarios = cargarInmueble(s.inmuebleId()).residentesActivos();
        } else {
            destinatarios = usuarios.listar(Usuario.RESIDENTE, true).stream()
                    .map(u -> Hidratador.referenciaUsuario(u.getId())).toList();
        }
        String tipo = s.tipoNotificacion() == null || s.tipoNotificacion().isBlank()
                ? TIPO_GENERAL : s.tipoNotificacion().trim().toUpperCase(Locale.ROOT);
        Notificacion notificacion = new Notificacion(null, destinatarios, s.mensaje().trim(), tipo, null);
        return enviar(notificacion, s.inmuebleId())
                .orElseThrow(() -> new ConflictoException("No hay destinatarios activos para esta notificacion"));
    }

    @Override
    @Transactional
    public NotificacionResponses.Notificacion notificarPagoPendiente(Long inmuebleId) {
        Notificacion notificacion = new Notificacion();
        notificacion.notificarPagoPendiente(cargarInmueble(inmuebleId));
        return enviar(notificacion, inmuebleId)
                .orElseThrow(() -> new ConflictoException("El inmueble no tiene residentes activos a quienes avisar"));
    }

    @Override
    @Transactional
    public Optional<NotificacionResponses.Notificacion> notificarReserva(Long reservaId) {
        ReservaView vista = reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reserva " + reservaId + " no existe"));
        ZonaComun zona = Hidratador.referenciaZona(vista.getZonaComunId());
        zona.setNombreZonaComun(vista.getNombreZonaComun());
        ReservaZonaComun reserva = new ReservaZonaComun(vista.getId(),
                Hidratador.referenciaResidente(vista.getDuenoReservaId()), zona, vista.getFechaHoraInicio(),
                vista.getFechaHoraFin());
        Notificacion notificacion = new Notificacion();
        notificacion.notificarReserva(reserva);
        return enviar(notificacion, null);
    }

    @Override
    @Transactional
    public Optional<NotificacionResponses.Notificacion> notificarActualizacionDePago(Long inmuebleId) {
        Inmueble inmueble = cargarInmueble(inmuebleId);
        Notificacion notificacion = new Notificacion();
        notificacion.notificarActualizacionDePago(inmueble, inmueble.residentesActivos());
        return enviar(notificacion, inmuebleId);
    }

    @Override
    @Transactional
    public Optional<NotificacionResponses.Notificacion> notificarVisita(Long inmuebleId, String nombreVisitante) {
        Inmueble inmueble = cargarInmueble(inmuebleId);
        Notificacion notificacion = new Notificacion();
        notificacion.notificarVisita(inmueble);
        if (nombreVisitante != null && !nombreVisitante.isBlank()) {
            notificacion.setMensaje("Ingreso su visita " + nombreVisitante.trim() + " al conjunto (inmueble "
                    + inmueble.getDireccionInmueble() + ").");
        }
        return enviar(notificacion, inmuebleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<NotificacionResponses.Notificacion> consultarNotificacion(Long usuarioId, SolicitudPagina pagina) {
        List<NotificacionResponses.Notificacion> filas = notificaciones.listarDeUsuario(usuarioId, pagina.limite(),
                pagina.desplazamiento()).stream().map(NotificacionMapper::de).toList();
        return Pagina.de(filas, notificaciones.contarDeUsuario(usuarioId), pagina);
    }

    @Override
    @Transactional
    public void marcarNotificacionLeida(Long usuarioId) {
        usuarios.actualizarNotificacionLeida(usuarioId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionResponses.Lectura consultarConfirmacionLectura(Long notificacionId) {
        if (!notificaciones.existePorId(notificacionId)) {
            throw new RecursoNoEncontradoException("La notificacion " + notificacionId + " no existe");
        }
        return NotificacionMapper.lectura(notificacionId, notificaciones.listarLectura(notificacionId));
    }

    // ------------------------------------------------------------------ internos

    /** Entrega la notificacion (dominio) y la guarda. Vacio si no tiene destinatarios. */
    private Optional<NotificacionResponses.Notificacion> enviar(Notificacion notificacion, Long inmuebleId) {
        if (!Boolean.TRUE.equals(notificacion.enviarNotificacion())) {
            return Optional.empty();
        }
        Long id = notificaciones.insertar(notificacion);
        List<Long> destinatarios = notificacion.getUsuarios().stream().map(Usuario::getId).distinct().toList();
        notificaciones.agregarDestinatarios(id, destinatarios);
        usuarios.marcarNoLeida(destinatarios);
        if (inmuebleId != null) {
            notificaciones.vincularInmueble(inmuebleId, id);
        }
        return Optional.of(NotificacionMapper.de(id, notificacion.getMensaje(), notificacion.getTipoNotificacion(),
                notificacion.getFechaEnvio()));
    }

    private Inmueble cargarInmueble(Long inmuebleId) {
        InmuebleBasicoView base = inmuebles.buscarBasico(inmuebleId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe"));
        List<UsuarioListaView> residentes = usuarios.listarPorInmueble(inmuebleId);
        return Hidratador.inmueble(base, residentes, liquidaciones.buscarVigenteDeInmueble(inmuebleId));
    }
}
