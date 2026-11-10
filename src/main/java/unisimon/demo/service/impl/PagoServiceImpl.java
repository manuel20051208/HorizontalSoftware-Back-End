package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.bootstrap.PasarelaProperties;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.PagoMapper;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.model.Pago;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.policy.AccesoInmueblePolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.PagoService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pagos de administracion. Las reglas del pago (monto, estados, comprobante) viven en {@link Pago}. */
@Service
public class PagoServiceImpl implements PagoService {

    static final Set<String> METODOS_PASARELA = Set.of("PSE", "TARJETA", "NEQUI");
    static final Set<String> ESTADOS = Set.of(Pago.PENDIENTE, Pago.APROBADO, Pago.RECHAZADO, Pago.ANULADO,
            Pago.REEMBOLSADO);
    static final int LIMITE_LISTADO = 200;

    private final PagoRepository pagos;
    private final LiquidacionRepository liquidaciones;
    private final PasarelaPagoRepository pasarelas;
    private final ResidenteRepository residentes;
    private final InmuebleRepository inmuebles;
    private final LiquidacionService liquidacionService;
    private final AccesoInmueblePolicy accesoInmueble;
    private final Clock reloj;
    private final PasarelaProperties configPasarela;

    public PagoServiceImpl(PagoRepository pagos, LiquidacionRepository liquidaciones,
                           PasarelaPagoRepository pasarelas, ResidenteRepository residentes,
                           InmuebleRepository inmuebles, LiquidacionService liquidacionService,
                           AccesoInmueblePolicy accesoInmueble, Clock reloj, PasarelaProperties configPasarela) {
        this.configPasarela = configPasarela;
        this.pagos = pagos;
        this.liquidaciones = liquidaciones;
        this.pasarelas = pasarelas;
        this.residentes = residentes;
        this.inmuebles = inmuebles;
        this.liquidacionService = liquidacionService;
        this.accesoInmueble = accesoInmueble;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public PagoResponses.IniciarPago registrarPago(Long residenteId, PagoRequests.IniciarPago s) {
        String metodo = s.metodoPago().trim().toUpperCase(Locale.ROOT);
        if (!METODOS_PASARELA.contains(metodo)) {
            throw new SolicitudInvalidaException("metodoPago debe ser PSE, TARJETA o NEQUI");
        }
        ContextoResidenteView residente = contexto(residenteId);
        if (!Boolean.TRUE.equals(residente.getActive())) {
            throw new ProhibidoException("Su cuenta no esta activa");
        }
        BigDecimal saldo = liquidaciones.saldoPendiente(residente.getInmuebleId());
        if (saldo.signum() == 0) {
            throw new ConflictoException("El inmueble " + residente.getDireccionInmueble() + " no tiene saldo pendiente");
        }
        PasarelaView vistaPasarela = pasarelas.buscarPrincipal()
                .orElseThrow(() -> new ConflictoException("La pasarela de pagos aun no esta configurada"));
        if (!configPasarela.listaParaCobrar()) {
            // Antes de insertar el pago: no se cobra con llaves faltantes, invalidas o de ambientes mezclados
            // (se cobraria pero el webhook nunca podria confirmarlo) y no quedan pagos PENDIENTES huerfanos.
            throw new ConflictoException("La pasarela de pagos no esta bien configurada en el servidor "
                    + "(secretos, llaves o URLs). Contacte al administrador");
        }

        // El valor lo fija el servidor (saldo pendiente), nunca el cliente.
        Pago pago = new Pago(null, Hidratador.referenciaInmueble(residente.getInmuebleId()), null, saldo, null,
                "Administracion " + residente.getDireccionInmueble(), metodo, null);
        pago.setId(pagos.insertar(pago));
        PasarelaPago pasarela = Hidratador.pasarela(vistaPasarela);
        // El .env es la fuente de verdad del checkout (la tabla solo aporta id y estado ACTIVA/INACTIVA).
        pasarela.setUrlApi(configPasarela.urlApi().trim());
        pasarela.setApiKey(configPasarela.apiKey().trim());
        // dominio: asigna referencia y pasarela, y firma el checkout con el secreto (solo del entorno)
        String urlCheckout = pasarela.iniciarTransaccion(pago, configPasarela.integritySecret(),
                configPasarela.redirectUrl());
        pagos.actualizarTransaccion(pago.getId(), pago.getReferencia(), pasarela.getId());
        return new PagoResponses.IniciarPago(respuesta(pago.getId()), urlCheckout);
    }

    @Override
    @Transactional
    public PagoResponses.Pago conciliarPagosAdministracion(Long inmuebleId, PagoRequests.Conciliar s) {
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
        BigDecimal saldo = liquidaciones.saldoPendiente(inmuebleId);
        if (saldo.signum() == 0) {
            throw new ConflictoException("El inmueble no tiene saldo pendiente que conciliar");
        }
        String referencia = s.referencia() == null || s.referencia().isBlank()
                ? "CONC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : s.referencia().trim();
        Pago pago = new Pago(null, Hidratador.referenciaInmueble(inmuebleId), null, s.monto(),
                LocalDate.now(reloj), "Conciliacion de administracion", s.metodoPago().trim().toUpperCase(Locale.ROOT),
                referencia);
        if (!pago.validarMonto(saldo)) {
            throw new SolicitudInvalidaException("El monto debe ser igual al saldo pendiente del inmueble: " + saldo);
        }
        pago.setEstado(Pago.APROBADO);
        Long id = pagos.insertar(pago);
        liquidacionService.marcarInmueblePagado(inmuebleId);
        return respuesta(id);
    }

    @Override
    @Transactional
    public PagoResponses.Pago aprobarPago(Long pagoId) {
        PagoView vista = buscar(pagoId);
        if (Pago.APROBADO.equals(vista.getEstado())) {
            return PagoMapper.de(vista);   // idempotente: la pasarela puede avisar mas de una vez
        }
        if (!Pago.PENDIENTE.equals(vista.getEstado())) {
            throw new ConflictoException("El pago esta " + vista.getEstado() + " y no puede aprobarse");
        }
        Pago pago = Hidratador.pago(vista, liquidaciones.buscarVigenteDeInmueble(vista.getInmuebleId()));
        pago.setEstado(Pago.APROBADO);
        pago.setFechaPago(LocalDate.now(reloj));
        Liquidacion vigente = pago.actualizarEstado(pago);   // dominio: deja al dia la liquidacion vigente
        pagos.actualizarEstado(pagoId, pago.getEstado(), pago.getFechaPago());
        if (vigente != null) {
            liquidaciones.actualizarEstado(vigente.getId(), vigente.getEstado());
        }
        liquidacionService.marcarInmueblePagado(vista.getInmuebleId());   // y las anteriores que seguian debiendo
        return respuesta(pagoId);
    }

    @Override
    @Transactional
    public PagoResponses.Pago rechazarPago(Long pagoId) {
        PagoView vista = buscar(pagoId);
        if (Pago.RECHAZADO.equals(vista.getEstado())) {
            return PagoMapper.de(vista);
        }
        if (!Pago.PENDIENTE.equals(vista.getEstado())) {
            throw new ConflictoException("El pago esta " + vista.getEstado() + " y no puede rechazarse");
        }
        pagos.actualizarEstado(pagoId, Pago.RECHAZADO, null);
        return respuesta(pagoId);
    }

    @Override
    @Transactional
    public PagoResponses.Pago anularPago(Long pagoId) {
        PagoView vista = buscar(pagoId);
        Pago pago = Hidratador.pago(vista);
        pago.anularPago();   // dominio: solo PENDIENTE (si no, IllegalStateException -> 409)
        pagos.actualizarEstado(pagoId, pago.getEstado(), vista.getFechaPago());
        return respuesta(pagoId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponses.Comprobante generarComprobante(Long pagoId, UsuarioAutenticado usuario) {
        PagoView vista = buscar(pagoId);
        accesoInmueble.verificar(usuario, vista.getInmuebleId());
        String texto = Hidratador.pago(vista).generarComprobante();
        return new PagoResponses.Comprobante(vista.getReferencia(), texto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponses.Pago> consultarHistorialPago(Long residenteId) {
        return pagos.listarPorInmueble(contexto(residenteId).getInmuebleId()).stream().map(PagoMapper::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponses.Pago consultarPagoDeResidente(Long residenteId, Long pagoId) {
        PagoView vista = buscar(pagoId);
        if (!vista.getInmuebleId().equals(contexto(residenteId).getInmuebleId())) {
            throw new ProhibidoException("Este pago pertenece a otro inmueble");
        }
        return PagoMapper.de(vista);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponses.Pago> listar(Long inmuebleId, String estado) {
        String estadoNormalizado = null;
        if (estado != null && !estado.isBlank()) {
            estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS.contains(estadoNormalizado)) {
                throw new SolicitudInvalidaException("estado no es valido: " + estado);
            }
        }
        return pagos.listar(inmuebleId, estadoNormalizado, LIMITE_LISTADO).stream().map(PagoMapper::de).toList();
    }

    // ------------------------------------------------------------------ internos

    private ContextoResidenteView contexto(Long residenteId) {
        return residentes.buscarContexto(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"));
    }

    private PagoView buscar(Long pagoId) {
        return pagos.buscarPorId(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pago " + pagoId + " no existe"));
    }

    private PagoResponses.Pago respuesta(Long pagoId) {
        return PagoMapper.de(pagos.buscarPorId(pagoId).orElseThrow());
    }
}
