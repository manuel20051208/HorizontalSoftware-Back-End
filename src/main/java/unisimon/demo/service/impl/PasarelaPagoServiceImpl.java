package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.bootstrap.PasarelaProperties;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.PasarelaNoDisponibleException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.integration.wompi.ClienteWompi;
import unisimon.demo.integration.wompi.FirmaEventosWompi;
import unisimon.demo.integration.wompi.TransaccionWompi;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.PagoMapper;
import unisimon.demo.model.Pago;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.policy.AccesoInmueblePolicy;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.PasarelaPagoService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

/** Configuracion de la pasarela y procesamiento de sus avisos (webhook). */
@Service
@RequiredArgsConstructor
public class PasarelaPagoServiceImpl implements PasarelaPagoService {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagoServiceImpl.class);
    private static final Set<String> ESTADOS_PASARELA = Set.of(PasarelaPago.ACTIVA, PasarelaPago.INACTIVA);
    static final String EVENTO_TRANSACCION = "transaction.updated";
    private static final String MONEDA = "COP";
    /** Un pago en linea sin transaccion en Wompi pasado este tiempo se da por abandonado y se anula. */
    static final Duration VENCIMIENTO_SIN_TRANSACCION = Duration.ofHours(2);
    /** Tope de pagos revisados por corrida, para no saturar la API de Wompi. */
    static final int LOTE_SINCRONIZACION = 100;

    private final PasarelaPagoRepository pasarelas;
    private final PagoRepository pagos;
    private final PagoService pagoService;
    private final LiquidacionService liquidacionService;
    private final NotificacionService notificaciones;
    private final AccesoInmueblePolicy accesoInmueble;
    private final PasarelaProperties config;
    private final ClienteWompi clienteWompi;
    private final Clock reloj;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(NombresCache.PASARELA_PAGO)
    public PagoResponses.Pasarela obtener() {
        return PagoMapper.pasarela(principal());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.PASARELA_PAGO, allEntries = true)
    /**
     * Solo activa o desactiva la pasarela. Nombre, URL y llaves vienen del .env (se sincronizan al arrancar):
     * si se escribieran aqui se perderian en el siguiente reinicio.
     */
    public PagoResponses.Pasarela configurar(PagoRequests.ConfigurarPasarela s) {
        String estado = s.estado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_PASARELA.contains(estado)) {
            throw new SolicitudInvalidaException("estado debe ser ACTIVA o INACTIVA");
        }
        PasarelaView actual = principal();
        pasarelas.actualizar(actual.getId(), actual.getNombrePasarela(), actual.getUrlApi(), actual.getApiKey(), estado);
        return PagoMapper.pasarela(principal());
    }

    /**
     * 1) Verifica la firma del evento con el secreto de eventos (nadie sin el secreto puede fabricarlo).
     * 2) No confia en el contenido del aviso: consulta la transaccion en la API de Wompi con la llave privada.
     * 3) Aprueba solo si Wompi dice APPROVED y el monto y la moneda coinciden con el pago del servidor.
     */
    @Override
    @Transactional
    public void confirmarTransaccion(Map<String, Object> evento) {
        if (PasarelaProperties.vacio(config.eventsSecret())) {
            throw new ConflictoException("La pasarela de pagos no tiene configurado el secreto de eventos");
        }
        if (!FirmaEventosWompi.esValida(evento, config.eventsSecret())) {
            throw new NoAutenticadoException("Firma del evento de la pasarela invalida");
        }
        if (!EVENTO_TRANSACCION.equals(evento.get("event"))) {
            return;   // otros eventos (p. ej. nequi_token.updated) no afectan pagos
        }
        Object id = FirmaEventosWompi.valor(evento, "data.transaction.id");
        if (id == null || id.toString().isBlank()) {
            throw new SolicitudInvalidaException("El evento no trae el id de la transaccion");
        }

        TransaccionWompi tx = clienteWompi.consultarTransaccion(id.toString());   // fuente de verdad
        if (tx.referencia() == null) {
            throw new PasarelaNoDisponibleException("La pasarela respondio una transaccion sin referencia");
        }
        PagoView pago = pagos.buscarPorReferencia(tx.referencia())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un pago con esa referencia"));
        aplicarResultado(pago, tx);
    }

    /**
     * Respaldo del webhook: revisa en Wompi los pagos en linea que siguen PENDIENTES. Aplica el resultado si Wompi
     * ya lo tiene, y anula los que nunca llegaron a crear una transaccion (checkout abandonado) pasado
     * {@link #VENCIMIENTO_SIN_TRANSACCION}. Un pago que Wompi reporta PENDING no se anula: aun puede aprobarse.
     */
    @Override
    @Transactional
    public int sincronizarPendientes() {
        if (PasarelaProperties.vacio(config.privateKey()) || PasarelaProperties.vacio(config.urlApiRest())) {
            return 0;   // sin llave privada no se puede consultar Wompi
        }
        LocalDateTime limite = LocalDateTime.now(reloj).minus(VENCIMIENTO_SIN_TRANSACCION);
        int actualizados = 0;
        for (PagoView pago : pagos.listarPendientesEnLinea(LOTE_SINCRONIZACION)) {
            Optional<TransaccionWompi> tx;
            try {
                tx = clienteWompi.buscarPorReferencia(pago.getReferencia());
            } catch (PasarelaNoDisponibleException e) {
                log.warn("Wompi no respondio al sincronizar los pagos pendientes; se reintenta en la proxima corrida");
                break;   // si Wompi esta caido, los demas tambien fallarian
            }
            if (tx.isPresent()) {
                if (aplicarResultado(pago, tx.get())) {
                    actualizados++;
                }
            } else if (pago.getCreadoEn() != null && pago.getCreadoEn().isBefore(limite)) {
                pagoService.anularPago(pago.getId());
                notificaciones.notificarActualizacionDePago(pago.getInmuebleId());
                actualizados++;
            }
        }
        return actualizados;
    }

    /**
     * Aplica al pago el resultado que Wompi confirma. Aprueba solo si el monto y la moneda coinciden con el pago
     * del servidor. Devuelve true si el pago cambio de estado.
     */
    private boolean aplicarResultado(PagoView pago, TransaccionWompi tx) {
        if (!Pago.PENDIENTE.equals(pago.getEstado())) {
            return false;   // idempotente: Wompi puede reenviar el mismo evento
        }
        if (tx.aprobada()) {
            long esperado = pago.getMonto().movePointRight(2).longValueExact();
            if (tx.montoEnCentavos() != esperado || !MONEDA.equals(tx.moneda())) {
                log.warn("Transaccion {} aprobada con monto/moneda ({} {}) distintos al pago {} ({} {}); "
                                + "el pago queda PENDIENTE para revision manual",
                        tx.id(), tx.montoEnCentavos(), tx.moneda(), pago.getId(), esperado, MONEDA);
                return false;
            }
            pagoService.aprobarPago(pago.getId());
        } else if (tx.fallida()) {
            pagoService.rechazarPago(pago.getId());
        } else {
            return false;   // PENDING: llegara otro evento (o la proxima sincronizacion) cuando termine
        }
        notificaciones.notificarActualizacionDePago(pago.getInmuebleId());   // notificarResultado(pago)
        return true;
    }

    @Override
    @Transactional
    public PagoResponses.EstadoPago consultarEstado(String referencia, UsuarioAutenticado usuario) {
        return consultarEstado(referencia, usuario, null);
    }

    /**
     * Si el pago sigue PENDIENTE, pregunta a Wompi antes de responder: al volver del checkout el residente ve el
     * resultado de una vez, sin esperar al webhook. Si Wompi no responde, se devuelve el estado guardado.
     */
    @Override
    @Transactional
    public PagoResponses.EstadoPago consultarEstado(String referencia, UsuarioAutenticado usuario, Long inmuebleId) {
        PagoView pago = pagos.buscarPorReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un pago con esa referencia"));
        if (inmuebleId != null && !inmuebleId.equals(pago.getInmuebleId())) {
            throw new ProhibidoException("Ese pago pertenece a otro inmueble");
        }
        accesoInmueble.verificar(usuario, pago.getInmuebleId());
        if (Pago.PENDIENTE.equals(pago.getEstado()) && pago.getPasarelaId() != null
                && !PasarelaProperties.vacio(config.privateKey()) && !PasarelaProperties.vacio(config.urlApiRest())) {
            try {
                Optional<TransaccionWompi> tx = clienteWompi.buscarPorReferencia(referencia);
                if (tx.isPresent() && aplicarResultado(pago, tx.get())) {
                    pago = pagos.buscarPorReferencia(referencia).orElseThrow();
                }
            } catch (PasarelaNoDisponibleException e) {
                log.warn("Wompi no respondio al consultar el pago {}; se devuelve el estado guardado", referencia);
            }
        }
        return new PagoResponses.EstadoPago(referencia, pago.getEstado());
    }

    @Override
    @Transactional
    public PagoResponses.Pago reembolsar(Long pagoId) {
        PagoView vista = pagos.buscarPorId(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pago " + pagoId + " no existe"));
        Pago pago = Hidratador.pago(vista);
        PasarelaPago pasarela = Hidratador.pasarela(principal());
        if (!pasarela.reembolsar(pago)) {   // dominio: solo los pagos aprobados se reembolsan
            throw new ConflictoException("Solo se reembolsan pagos APROBADOS; este esta " + vista.getEstado());
        }
        pagos.actualizarEstado(pagoId, pago.getEstado(), vista.getFechaPago());
        liquidacionService.reabrirVigente(vista.getInmuebleId());
        notificaciones.notificarActualizacionDePago(vista.getInmuebleId());
        return PagoMapper.de(pagos.buscarPorId(pagoId).orElseThrow());
    }

    private PasarelaView principal() {
        return pasarelas.buscarPrincipal()
                .orElseThrow(() -> new ConflictoException("La pasarela de pagos aun no esta configurada"));
    }
}
