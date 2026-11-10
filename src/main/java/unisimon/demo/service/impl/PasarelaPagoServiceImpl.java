package unisimon.demo.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/** Configuracion de la pasarela y procesamiento de sus avisos (webhook). */
@Service
public class PasarelaPagoServiceImpl implements PasarelaPagoService {

    private static final Set<String> ESTADOS_PASARELA = Set.of(PasarelaPago.ACTIVA, PasarelaPago.INACTIVA);

    private final PasarelaPagoRepository pasarelas;
    private final PagoRepository pagos;
    private final PagoService pagoService;
    private final LiquidacionService liquidacionService;
    private final NotificacionService notificaciones;
    private final AccesoInmueblePolicy accesoInmueble;

    public PasarelaPagoServiceImpl(PasarelaPagoRepository pasarelas, PagoRepository pagos, PagoService pagoService,
                                   LiquidacionService liquidacionService, NotificacionService notificaciones,
                                   AccesoInmueblePolicy accesoInmueble) {
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
    public PagoResponses.Pasarela configurar(PagoRequests.ConfigurarPasarela s) {
        String estado = s.estado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_PASARELA.contains(estado)) {
            throw new SolicitudInvalidaException("estado debe ser ACTIVA o INACTIVA");
        }
        PasarelaPago nueva = new PasarelaPago(null, s.nombrePasarela().trim(), s.urlApi().trim(), s.apiKey().trim());
        nueva.setEstado(estado);
        pasarelas.buscarPrincipal().ifPresentOrElse(
                actual -> pasarelas.actualizar(actual.getId(), nueva.getNombrePasarela(), nueva.getUrlApi(),
                        nueva.getApiKey(), nueva.getEstado()),
                () -> pasarelas.insertar(nueva));
        return PagoMapper.pasarela(principal());
    }

    @Override
    @Transactional
    public PagoResponses.Pago confirmarTransaccion(String apiKey, PagoRequests.Webhook aviso) {
        PasarelaView pasarela = principal();
        // Comparacion en tiempo constante para no filtrar la llave por tiempos de respuesta.
        boolean llaveValida = apiKey != null && MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), pasarela.getApiKey().getBytes(StandardCharsets.UTF_8));
        if (!llaveValida) {
            throw new NoAutenticadoException("Llave de la pasarela invalida");
        }
        PagoView pago = pagos.buscarPorReferencia(aviso.referencia().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un pago con esa referencia"));
        boolean estabaPendiente = Pago.PENDIENTE.equals(pago.getEstado());

        PagoResponses.Pago resultado = switch (aviso.estado().trim().toUpperCase(Locale.ROOT)) {
            case Pago.APROBADO -> pagoService.aprobarPago(pago.getId());
            case Pago.RECHAZADO -> pagoService.rechazarPago(pago.getId());
            default -> throw new SolicitudInvalidaException("estado debe ser APROBADO o RECHAZADO");
        };
        if (estabaPendiente) {
            notificaciones.notificarActualizacionDePago(pago.getInmuebleId());   // notificarResultado(pago)
        }
        return resultado;
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
