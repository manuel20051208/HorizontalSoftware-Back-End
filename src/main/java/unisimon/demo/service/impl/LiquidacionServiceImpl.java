package unisimon.demo.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.LiquidacionMapper;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.LiquidacionRepository.LiquidacionView;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.service.LiquidacionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ciclo de vida de las liquidaciones. Las reglas (total, vencimiento, mora) las ejecuta la entidad
 * {@link Liquidacion}; aqui solo se orquesta y se persiste con UPDATE por columna.
 */
@Service
public class LiquidacionServiceImpl implements LiquidacionService {

    /** Dia del mes en que vence por defecto la administracion. */
    static final int DIA_VENCIMIENTO = 10;

    private final LiquidacionRepository liquidaciones;
    private final InmuebleRepository inmuebles;
    private final ParqueaderoRepository parqueaderos;

    public LiquidacionServiceImpl(LiquidacionRepository liquidaciones, InmuebleRepository inmuebles,
                                  ParqueaderoRepository parqueaderos) {
        this.liquidaciones = liquidaciones;
        this.inmuebles = inmuebles;
        this.parqueaderos = parqueaderos;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.Liquidacion generarLiquidacion(LiquidacionRequests.Generar s) {
        InmuebleBasicoView inmueble = inmuebles.buscarBasico(s.inmuebleId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El inmueble " + s.inmuebleId() + " no existe"));
        if (!Boolean.TRUE.equals(inmueble.getIsActive())) {
            throw new ConflictoException("El inmueble " + inmueble.getDireccionInmueble() + " esta inactivo");
        }
        LocalDate periodo = s.periodo().withDayOfMonth(1);
        if (liquidaciones.existePorInmuebleYPeriodo(inmueble.getId(), periodo)) {
            throw new ConflictoException("El inmueble " + inmueble.getDireccionInmueble()
                    + " ya tiene liquidacion del periodo " + periodo);
        }
        Optional<LiquidacionView> previa = liquidaciones.buscarVigenteDeInmueble(inmueble.getId());
        BigDecimal cuota = s.cuotaAdministracion() != null ? s.cuotaAdministracion()
                : previa.map(LiquidacionView::getCuotaAdministracion).orElseThrow(() -> new SolicitudInvalidaException(
                "Indique cuotaAdministracion: el inmueble aun no tiene liquidaciones de las que copiarla"));
        BigDecimal servicios = s.valorServiciosComunes() != null ? s.valorServiciosComunes()
                : previa.map(LiquidacionView::getValorServiciosComunes).orElse(BigDecimal.ZERO);
        return crear(inmueble.getId(), periodo, s.fechaLimite(), cuota, servicios, previa);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.ResultadoMasiva generarMasiva(LiquidacionRequests.GenerarMasiva s) {
        LocalDate periodo = s.periodo().withDayOfMonth(1);
        int generadas = 0;
        List<LiquidacionResponses.Omitida> omitidas = new ArrayList<>();
        for (Long inmuebleId : inmuebles.listarIdsActivos()) {
            if (liquidaciones.existePorInmuebleYPeriodo(inmuebleId, periodo)) {
                omitidas.add(new LiquidacionResponses.Omitida(inmuebleId, "Ya tiene liquidacion de ese periodo"));
                continue;
            }
            Optional<LiquidacionView> previa = liquidaciones.buscarVigenteDeInmueble(inmuebleId);
            if (previa.isEmpty()) {
                omitidas.add(new LiquidacionResponses.Omitida(inmuebleId,
                        "Sin liquidacion previa de la que copiar tarifas: genere la primera de forma individual"));
                continue;
            }
            crear(inmuebleId, periodo, s.fechaLimite(), previa.get().getCuotaAdministracion(),
                    previa.get().getValorServiciosComunes(), previa);
            generadas++;
        }
        return new LiquidacionResponses.ResultadoMasiva(generadas, omitidas);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.Liquidacion configurarTarifas(Long inmuebleId, InmuebleRequests.Tarifas tarifas) {
        Liquidacion liquidacion = vigentePendiente(inmuebleId);
        liquidacion.setCuotaAdministracion(tarifas.cuotaAdministracion());
        liquidacion.setValorServiciosComunes(tarifas.valorServiciosComunes());
        liquidacion.calcularTotal();
        guardarValores(liquidacion);
        if (tarifas.valorParqueadero() != null) {
            if (parqueaderos.buscarPorInmueble(inmuebleId).isPresent()) {
                parqueaderos.actualizarValorMensual(inmuebleId, tarifas.valorParqueadero());
            } else if (tarifas.valorParqueadero().signum() > 0) {
                parqueaderos.insertar(inmuebleId, tarifas.valorParqueadero());
            }
        }
        return respuesta(liquidacion.getId());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.Liquidacion configurarMultas(Long inmuebleId, InmuebleRequests.Multas multas) {
        Liquidacion liquidacion = vigentePendiente(inmuebleId);
        liquidacion.setValorMultas(multas.valorMultas());
        liquidacion.calcularTotal();
        guardarValores(liquidacion);
        return respuesta(liquidacion.getId());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.Liquidacion marcarEnMora(Long liquidacionId) {
        Liquidacion liquidacion = cargar(liquidacionId);
        liquidacion.marcarEnMora();
        liquidaciones.actualizarEstado(liquidacionId, liquidacion.getEstado());
        return respuesta(liquidacionId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public LiquidacionResponses.Liquidacion marcarAlDia(Long liquidacionId) {
        Liquidacion liquidacion = cargar(liquidacionId);
        liquidacion.marcarAlDia();
        liquidaciones.actualizarEstado(liquidacionId, liquidacion.getEstado());
        return respuesta(liquidacionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<LiquidacionResponses.Liquidacion> listar(Long inmuebleId, String estado, LocalDate periodo,
                                                         SolicitudPagina pagina) {
        String estadoNormalizado = null;
        if (estado != null && !estado.isBlank()) {
            estadoNormalizado = estado.trim().toUpperCase();
            if (!Set.of(Liquidacion.PENDIENTE, Liquidacion.AL_DIA, Liquidacion.EN_MORA).contains(estadoNormalizado)) {
                throw new SolicitudInvalidaException("estado debe ser PENDIENTE, AL_DIA o EN_MORA");
            }
        }
        LocalDate primerDia = periodo == null ? null : periodo.withDayOfMonth(1);
        List<LiquidacionResponses.Liquidacion> filas = liquidaciones.listar(inmuebleId, estadoNormalizado, primerDia,
                        pagina.limite(), pagina.desplazamiento())
                .stream().map(LiquidacionMapper::de).toList();
        return Pagina.de(filas, liquidaciones.contar(inmuebleId, estadoNormalizado, primerDia), pagina);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public int marcarVencidas() {
        List<Long> vencidas = liquidaciones.listarPorEstado(Liquidacion.PENDIENTE).stream()
                .filter(v -> Hidratador.liquidacion(v).estaVencida())
                .map(LiquidacionView::getId)
                .toList();
        if (!vencidas.isEmpty()) {
            liquidaciones.actualizarEstadoPorIds(vencidas, Liquidacion.EN_MORA);
        }
        return vencidas.size();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public void marcarInmueblePagado(Long inmuebleId) {
        liquidaciones.marcarPagadasDeInmueble(inmuebleId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public void reabrirVigente(Long inmuebleId) {
        liquidaciones.buscarVigenteDeInmueble(inmuebleId)
                .map(Hidratador::liquidacion)
                .filter(l -> Liquidacion.AL_DIA.equals(l.getEstado()))
                .ifPresent(l -> {
                    l.setEstado(Liquidacion.PENDIENTE);
                    if (l.estaVencida()) {
                        l.marcarEnMora();
                    }
                    liquidaciones.actualizarEstado(l.getId(), l.getEstado());
                });
    }

    // ------------------------------------------------------------------ internos

    private LiquidacionResponses.Liquidacion crear(Long inmuebleId, LocalDate periodo, LocalDate fechaLimite,
                                                   BigDecimal cuota, BigDecimal servicios,
                                                   Optional<LiquidacionView> previa) {
        LocalDate limite = fechaLimite != null ? fechaLimite : periodo.withDayOfMonth(DIA_VENCIMIENTO);
        Liquidacion nueva = new Liquidacion(null, Hidratador.referenciaInmueble(inmuebleId), periodo, limite,
                cuota, servicios, BigDecimal.ZERO);
        // Lo que el mes anterior no pago queda en mora al abrirse el nuevo periodo.
        previa.map(Hidratador::liquidacion)
                .filter(anterior -> !Liquidacion.AL_DIA.equals(anterior.getEstado()))
                .ifPresent(anterior -> {
                    anterior.marcarEnMora();
                    liquidaciones.actualizarEstado(anterior.getId(), anterior.getEstado());
                });
        Long id = liquidaciones.insertar(nueva);
        inmuebles.actualizarLiquidacionActual(inmuebleId, id);
        return respuesta(id);
    }

    private Liquidacion vigentePendiente(Long inmuebleId) {
        Liquidacion vigente = liquidaciones.buscarVigenteDeInmueble(inmuebleId)
                .map(Hidratador::liquidacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El inmueble " + inmuebleId + " no tiene una liquidacion vigente"));
        if (!Liquidacion.PENDIENTE.equals(vigente.getEstado())) {
            throw new ConflictoException("Solo se puede ajustar una liquidacion PENDIENTE; esta "
                    + vigente.getEstado() + ". Genere la del nuevo periodo con las tarifas deseadas");
        }
        return vigente;
    }

    private Liquidacion cargar(Long liquidacionId) {
        return liquidaciones.buscarPorId(liquidacionId).map(Hidratador::liquidacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("La liquidacion " + liquidacionId + " no existe"));
    }

    private void guardarValores(Liquidacion l) {
        liquidaciones.actualizarValores(l.getId(), l.getCuotaAdministracion(), l.getValorServiciosComunes(),
                l.getValorMultas(), l.getTotal());
    }

    private LiquidacionResponses.Liquidacion respuesta(Long id) {
        return LiquidacionMapper.de(liquidaciones.buscarPorId(id).orElseThrow());
    }
}
