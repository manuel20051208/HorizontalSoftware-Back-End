package unisimon.demo.service.impl;

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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuracion de la pasarela y procesamiento de sus avisos (webhook). */
@Service
public class PasarelaPagoServiceImpl implements PasarelaPagoService {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagoServiceImpl.class);
    private static final Set<String> ESTADOS_PASARELA = Set.of(PasarelaPago.ACTIVA, PasarelaPago.INACTIVA);
    static final String EVENTO_TRANSACCION = "transaction.updated";
    private static final String MONEDA = "COP";

    private final PasarelaPagoRepository pasarelas;
    private final PagoRepository pagos;
    private final PagoService pagoService;
    private final LiquidacionService liquidacionService;
    private final NotificacionService notificaciones;
    private final AccesoInmueblePolicy accesoInmueble;
    private final PasarelaProperties config;
    private final ClienteWompi clienteWompi;

    public PasarelaPagoServiceImpl(PasarelaPagoRepository pasarelas, PagoRepository pagos, PagoService pagoService,
                                   LiquidacionService liquidacionService, NotificacionService notificaciones,
                                   AccesoInmueblePolicy accesoInmueble, PasarelaProperties config,
                                   ClienteWompi clienteWompi) {
        this.config = config;
        this.clienteWompi = clienteWompi;
        this.pasarelas = pasarelas;
        this.pagos = pagos;
        this.pagoService = pagoService;
        this.liquidacionService = liquidacionService;
        this.notificaciones = notificaciones;
        this.accesoInmueble = accesoInmueble;
    }

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
        if (!Pago.PENDIENTE.equals(pago.getEstado())) {
            return;   // idempotente: Wompi puede reenviar el mismo evento
        }

        if (tx.aprobada()) {
            long esperado = pago.getMonto().movePointRight(2).longValueExact();
            if (tx.montoEnCentavos() != esperado || !MONEDA.equals(tx.moneda())) {
                log.warn("Transaccion {} aprobada con monto/moneda ({} {}) distintos al pago {} ({} {}); "
                                + "el pago queda PENDIENTE para revision manual",
                        tx.id(), tx.montoEnCentavos(), tx.moneda(), pago.getId(), esperado, MONEDA);
                return;
            }
            pagoService.aprobarPago(pago.getId());
        } else if (tx.fallida()) {
            pagoService.rechazarPago(pago.getId());
        } else {
            return;   // PENDING: llegara otro evento cuando termine
        }
        notificaciones.notificarActualizacionDePago(pago.getInmuebleId());   // notificarResultado(pago)
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponses.EstadoPago consultarEstado(String referencia, UsuarioAutenticado usuario) {
        PagoView pago = pagos.buscarPorReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un pago con esa referencia"));
        accesoInmueble.verificar(usuario, pago.getInmuebleId());
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
