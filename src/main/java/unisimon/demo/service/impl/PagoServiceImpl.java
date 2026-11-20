package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.bootstrap.PasarelaProperties;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
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
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.support.InmuebleDelResidente;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pagos de administracion. Las reglas del pago (monto, estados, comprobante) viven en {@link Pago}. */
@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    static final Set<String> METODOS_PASARELA = Set.of("PSE", "TARJETA", "NEQUI");
    static final Set<String> ESTADOS = Set.of(Pago.PENDIENTE, Pago.APROBADO, Pago.RECHAZADO, Pago.ANULADO,
            Pago.REEMBOLSADO);

    private final PagoRepository pagos;
    private final LiquidacionRepository liquidaciones;
    private final PasarelaPagoRepository pasarelas;
    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final LiquidacionService liquidacionService;
    private final AccesoInmueblePolicy accesoInmueble;
    private final InmuebleDelResidente inmuebleDelResidente;
    private final Clock reloj;
    private final PasarelaProperties configPasarela;

    @Override
    @Transactional
    public PagoResponses.IniciarPago registrarPago(Long residenteId, Long inmuebleId, PagoRequests.IniciarPago s) {
        String metodo = s.metodoPago().trim().toUpperCase(Locale.ROOT);
        if (!METODOS_PASARELA.contains(metodo)) {
            throw new SolicitudInvalidaException("metodoPago debe ser PSE, TARJETA o NEQUI");
        }
        InmuebleBasicoView elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId);
        if (!Boolean.TRUE.equals(cuentaActiva(residenteId))) {
            throw new ProhibidoException("Su cuenta no esta activa");
        }
        BigDecimal saldo = liquidaciones.saldoPendiente(elegido.getId());
        if (saldo.signum() == 0) {
            throw new ConflictoException("El inmueble " + elegido.getDireccionInmueble() + " no tiene saldo pendiente");
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
        Pago pago = new Pago(null, Hidratador.referenciaInmueble(elegido.getId()), saldo, null,
                "Administracion " + elegido.getDireccionInmueble(), metodo, null);
        pago.setId(pagos.insertar(pago));
        pagos.registrarCreacion(pago.getId(), LocalDateTime.now(reloj));   // para vencerlo si se abandona
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
        Pago pago = new Pago(null, Hidratador.referenciaInmueble(inmuebleId), s.monto(),
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
        return generarComprobante(pagoId, usuario, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponses.Comprobante generarComprobante(Long pagoId, UsuarioAutenticado usuario, Long inmuebleId) {
        PagoView vista = buscar(pagoId);
        if (inmuebleId != null && !inmuebleId.equals(vista.getInmuebleId())) {
            throw new ProhibidoException("Ese pago pertenece a otro inmueble");
        }
        accesoInmueble.verificar(usuario, vista.getInmuebleId());
        String texto = Hidratador.pago(vista).generarComprobante();
        return new PagoResponses.Comprobante(vista.getReferencia(), texto);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PagoResponses.Pago> consultarHistorialPago(Long residenteId, Long inmuebleId,
                                                             SolicitudPagina pagina) {
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        List<PagoResponses.Pago> filas = pagos.listarPorInmueble(elegido, pagina.limite(), pagina.desplazamiento())
                .stream().map(PagoMapper::de).toList();
        return Pagina.de(filas, pagos.contarPorInmueble(elegido), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponses.Pago consultarPagoDeResidente(Long residenteId, Long inmuebleId, Long pagoId) {
        PagoView vista = buscar(pagoId);
        if (!vista.getInmuebleId().equals(inmuebleDelResidente.resolver(residenteId, inmuebleId).getId())) {
            throw new ProhibidoException("Este pago pertenece a otro inmueble");
        }
        return PagoMapper.de(vista);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PagoResponses.Pago> listar(Long inmuebleId, String estado, SolicitudPagina pagina) {
        String estadoNormalizado = null;
        if (estado != null && !estado.isBlank()) {
            estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS.contains(estadoNormalizado)) {
                throw new SolicitudInvalidaException("estado no es valido: " + estado);
            }
        }
        List<PagoResponses.Pago> filas = pagos.listar(inmuebleId, estadoNormalizado, pagina.limite(),
                pagina.desplazamiento()).stream().map(PagoMapper::de).toList();
        return Pagina.de(filas, pagos.contar(inmuebleId, estadoNormalizado), pagina);
    }

    // ------------------------------------------------------------------ internos

    private Boolean cuentaActiva(Long residenteId) {
        return usuarios.buscarCredencialesPorId(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no existe")).getActive();
    }

    private PagoView buscar(Long pagoId) {
        return pagos.buscarPorId(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pago " + pagoId + " no existe"));
    }

    private PagoResponses.Pago respuesta(Long pagoId) {
        return PagoMapper.de(pagos.buscarPorId(pagoId).orElseThrow());
    }
}
