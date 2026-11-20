package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.ReservaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.exception.ApiException;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.ReservaMapper;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.ReservaZonaComun;
import unisimon.demo.model.Residente;
import unisimon.demo.policy.EstadoReservaPolicy;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.ReservaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.repository.RompimientoRepository;
import unisimon.demo.repository.RompimientoRepository.LimitesReservaView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.ReservaZonaComunService;
import unisimon.demo.service.ZonaComunService;
import unisimon.demo.service.support.InmuebleDelResidente;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reservas de zonas comunes con fecha y hora.
 * <p>
 * Reservar y consultar disponibilidad aplican exactamente las mismas condiciones ({@link #evaluar}), en este orden:
 * inicio futuro, zona asignada y activa, residente habilitado, horario de la zona, reglas activas de la zona
 * (anticipacion minima y duracion maxima), cupo en ese intervalo y que no se cruce con otra reserva suya de la
 * misma zona. Las reglas que solo dependen de la reserva viven en {@link ReservaZonaComun}.
 */
@Service
@RequiredArgsConstructor
public class ReservaZonaComunServiceImpl implements ReservaZonaComunService {

    private final ReservaZonaComunRepository reservas;
    private final ZonaComunRepository zonas;
    private final ResidenteRepository residentes;
    private final RompimientoRepository reglas;
    private final NotificacionService notificaciones;
    private final ZonaComunService zonasComunes;
    private final InmuebleDelResidente inmuebleDelResidente;
    private final Clock reloj;

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public ReservaResponses.Reserva reservarZonaComun(Long residenteId, Long inmuebleId,
                                                     ReservaRequests.Reservar s) {
        ContextoResidenteView contexto = contexto(residenteId, inmuebleId);
        ZonaView vistaZona = zona(s.zonaComunId());
        LocalDateTime ahora = ahora();
        ReservaZonaComun reserva = construir(contexto, vistaZona, s.fechaHoraInicio(), s.fechaHoraFin());

        List<Incumplimiento> incumplimientos = evaluar(residenteId, contexto, vistaZona, reserva, ahora, null);
        if (!incumplimientos.isEmpty()) {
            throw incumplimientos.get(0).excepcion();
        }
        Long id = reservas.insertar(reserva);
        notificaciones.notificarReserva(id);
        return ReservaMapper.de(reservas.buscarPorId(id).orElseThrow(), ahora);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public void cancelarReservaZonaComun(Long residenteId, Long reservaId) {
        ReservaView vista = reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reserva " + reservaId + " no existe"));
        if (!vista.getDuenoReservaId().equals(residenteId)) {
            throw new ProhibidoException("Solo quien hizo la reserva puede cancelarla");
        }
        ReservaZonaComun reserva = new ReservaZonaComun(vista.getId(), null, null,
                Hidratador.referenciaInmueble(vista.getInmuebleId()),
                vista.getFechaHoraInicio(), vista.getFechaHoraFin());
        LocalDateTime ahora = ahora();
        if (!reserva.sePuedeCancelar(ahora)) {
            throw new ConflictoException("Solo se cancela una reserva que aun no empieza; esta "
                    + reserva.estado(ahora));
        }
        reservas.eliminar(reservaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<ReservaResponses.Reserva> verReservas(Long residenteId, Long inmuebleId, String estado,
                                                        SolicitudPagina pagina) {
        String filtro = EstadoReservaPolicy.normalizarFiltro(estado);
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        LocalDateTime ahora = ahora();
        List<ReservaResponses.Reserva> filas = reservas.listarPorResidenteEnInmueble(residenteId, elegido, filtro,
                        ahora, pagina.limite(), pagina.desplazamiento())
                .stream().map(v -> ReservaMapper.de(v, ahora)).toList();
        return Pagina.de(filas, reservas.contarPorResidenteEnInmueble(residenteId, elegido, filtro, ahora), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponses.Disponibilidad verificarDisponibilidad(Long residenteId, Long inmuebleId,
                                                                   Long zonaComunId,
                                                                   LocalDateTime inicio, LocalDateTime fin) {
        ContextoResidenteView contexto = contexto(residenteId, inmuebleId);
        ZonaView vistaZona = zona(zonaComunId);
        ReservaZonaComun reserva = construir(contexto, vistaZona, inicio, fin);

        List<Incumplimiento> incumplimientos = evaluar(residenteId, contexto, vistaZona, reserva, ahora(), null);
        String mensaje = incumplimientos.isEmpty() ? "Disponible" : incumplimientos.get(0).mensaje();
        return new ReservaResponses.Disponibilidad(zonaComunId, reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin(), cumple(incumplimientos, Motivo.HORARIO),
                cumple(incumplimientos, Motivo.REGLAS), cumple(incumplimientos, Motivo.CUPO),
                cumple(incumplimientos, Motivo.HABILITACION), incumplimientos.isEmpty(), mensaje);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<ZonaResponses.Zona> zonasDisponibles(Long residenteId, Long inmuebleId, SolicitudPagina pagina) {
        return zonasComunes.mostrarZonasComunes(
                inmuebleDelResidente.resolver(residenteId, inmuebleId).getId(), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponses.Reserva consultarReserva(Long residenteId, Long reservaId) {
        return ReservaMapper.de(propia(residenteId, reservaId), ahora());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public ReservaResponses.Reserva reprogramarReserva(Long residenteId, Long reservaId,
                                                      ReservaRequests.Reprogramar s) {
        ReservaView vista = propia(residenteId, reservaId);
        ReservaZonaComun actual = new ReservaZonaComun(vista.getId(), null, null,
                Hidratador.referenciaInmueble(vista.getInmuebleId()),
                vista.getFechaHoraInicio(), vista.getFechaHoraFin());
        LocalDateTime ahora = ahora();
        if (!actual.sePuedeCancelar(ahora)) {
            throw new ConflictoException("Solo se reprograma una reserva que aun no empieza; esta "
                    + actual.estado(ahora));
        }
        ContextoResidenteView contexto = contexto(residenteId, vista.getInmuebleId());
        ZonaView vistaZona = zona(vista.getZonaComunId());
        ReservaZonaComun nueva = construir(contexto, vistaZona, s.fechaHoraInicio(), s.fechaHoraFin());
        List<Incumplimiento> incumplimientos = evaluar(residenteId, contexto, vistaZona, nueva, ahora, reservaId);
        if (!incumplimientos.isEmpty()) {
            throw incumplimientos.get(0).excepcion();
        }
        reservas.actualizarIntervalo(reservaId, nueva.getFechaHoraInicio(), nueva.getFechaHoraFin());
        return ReservaMapper.de(reservas.buscarPorId(reservaId).orElseThrow(), ahora);
    }

    /** La reserva existe y la hizo este residente. */
    private ReservaView propia(Long residenteId, Long reservaId) {
        ReservaView vista = reservas.buscarPorId(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reserva " + reservaId + " no existe"));
        if (!vista.getDuenoReservaId().equals(residenteId)) {
            throw new ProhibidoException("La reserva " + reservaId + " no es suya");
        }
        return vista;
    }

    // ------------------------------------------------------------------ validacion comun

    /** Por que no se puede reservar, con el codigo HTTP que corresponde a cada motivo. */
    enum Motivo {
        FECHA(SolicitudInvalidaException::new),
        ZONA(ProhibidoException::new),
        HABILITACION(ProhibidoException::new),
        HORARIO(ConflictoException::new),
        REGLAS(ConflictoException::new),
        CUPO(ConflictoException::new),
        SOLAPE(ConflictoException::new);

        private final Function<String, ApiException> excepcion;

        Motivo(Function<String, ApiException> excepcion) {
            this.excepcion = excepcion;
        }
    }

    record Incumplimiento(Motivo motivo, String mensaje) {
        ApiException excepcion() {
            return motivo.excepcion.apply(mensaje);
        }
    }

    /**
     * Arma la reserva a validar con las horas truncadas al minuto. Un intervalo cuyo fin no es posterior al
     * inicio no se puede evaluar: responde 400 tanto al reservar como al consultar disponibilidad.
     */
    private ReservaZonaComun construir(ContextoResidenteView contexto, ZonaView vistaZona,
                                       LocalDateTime inicio, LocalDateTime fin) {
        Residente dueno = Hidratador.residente(contexto);
        Inmueble inmueble = dueno.getInmuebles().get(0);   // el elegido, con su estado y liquidacion
        ReservaZonaComun reserva = new ReservaZonaComun(null, dueno, Hidratador.zona(vistaZona), inmueble,
                inicio.truncatedTo(ChronoUnit.MINUTES), fin.truncatedTo(ChronoUnit.MINUTES));
        if (!reserva.intervaloValido()) {
            throw new SolicitudInvalidaException("fechaHoraFin debe ser posterior a fechaHoraInicio");
        }
        return reserva;
    }

    /** {@code excluir}: id de la reserva que se reprograma (no cuenta contra el cupo ni el solape); nulo al reservar. */
    private List<Incumplimiento> evaluar(Long residenteId, ContextoResidenteView contexto, ZonaView vistaZona,
                                         ReservaZonaComun reserva, LocalDateTime ahora, Long excluir) {
        List<Incumplimiento> incumplimientos = new ArrayList<>();
        String nombreZona = vistaZona.getNombreZonaComun();

        if (!reserva.getFechaHoraInicio().isAfter(ahora)) {
            incumplimientos.add(new Incumplimiento(Motivo.FECHA, "La reserva debe empezar en el futuro"));
        }
        boolean zonaAsignada = zonas.listarActivasDeInmueble(contexto.getInmuebleId()).stream()
                .anyMatch(z -> z.getId().equals(vistaZona.getId()));
        if (!zonaAsignada) {
            incumplimientos.add(new Incumplimiento(Motivo.ZONA,
                    "Su inmueble no tiene asignada la zona " + nombreZona + " o esta inactiva"));
        }
        if (!reserva.estaHabilitado(reserva.getDuenoReserva())) {
            incumplimientos.add(new Incumplimiento(Motivo.HABILITACION, "No esta habilitado para reservar: "
                    + "verifique que su cuenta este activa y que no tenga la administracion en mora"));
        }
        if (!reserva.validarHorario()) {
            incumplimientos.add(new Incumplimiento(Motivo.HORARIO, "El horario pedido esta fuera del horario de "
                    + nombreZona + " (" + vistaZona.getHoraInicio() + " - " + vistaZona.getHoraFin() + ")"));
        }
        LimitesReservaView limites = reglas.buscarLimitesDeZona(vistaZona.getId());
        Integer anticipacion = limites == null ? null : limites.getAnticipacionMinimaHoras();
        Integer duracionMaxima = limites == null ? null : limites.getDuracionMaximaHoras();
        if (!reserva.cumpleAnticipacion(ahora, anticipacion)) {
            incumplimientos.add(new Incumplimiento(Motivo.REGLAS, nombreZona + " se reserva con al menos "
                    + anticipacion + " horas de anticipacion"));
        }
        if (!reserva.cumpleDuracionMaxima(duracionMaxima)) {
            incumplimientos.add(new Incumplimiento(Motivo.REGLAS, nombreZona + " permite reservas de maximo "
                    + duracionMaxima + " horas"));
        }
        long solapadas = excluir == null
                ? reservas.contarSolapadas(vistaZona.getId(), reserva.getFechaHoraInicio(), reserva.getFechaHoraFin())
                : reservas.contarSolapadasExcepto(vistaZona.getId(), reserva.getFechaHoraInicio(),
                        reserva.getFechaHoraFin(), excluir);
        if (solapadas >= vistaZona.getCapacidad()) {
            incumplimientos.add(new Incumplimiento(Motivo.CUPO, nombreZona + " no tiene cupo en ese horario"));
        }
        boolean seCruza = excluir == null
                ? reservas.existeSolapadaDeResidente(residenteId, vistaZona.getId(), reserva.getFechaHoraInicio(),
                        reserva.getFechaHoraFin())
                : reservas.existeSolapadaDeResidenteExcepto(residenteId, vistaZona.getId(),
                        reserva.getFechaHoraInicio(), reserva.getFechaHoraFin(), excluir);
        if (seCruza) {
            incumplimientos.add(new Incumplimiento(Motivo.SOLAPE,
                    "Ya tiene una reserva de " + nombreZona + " que se cruza con ese horario"));
        }
        return incumplimientos;
    }

    private static boolean cumple(List<Incumplimiento> incumplimientos, Motivo motivo) {
        return incumplimientos.stream().noneMatch(i -> i.motivo() == motivo);
    }

    // ------------------------------------------------------------------ apoyo

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    private ContextoResidenteView contexto(Long residenteId, Long inmuebleId) {
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        return residentes.buscarContexto(residenteId, elegido)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"));
    }

    private ZonaView zona(Long zonaId) {
        return zonas.buscarPorId(zonaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La zona comun " + zonaId + " no existe"));
    }
}
