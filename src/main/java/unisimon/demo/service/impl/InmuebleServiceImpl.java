package unisimon.demo.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.InmuebleMapper;
import unisimon.demo.mapper.LiquidacionMapper;
import unisimon.demo.mapper.UsuarioMapper;
import unisimon.demo.mapper.ZonaMapper;
import unisimon.demo.model.Inmueble;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.service.InmuebleService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Alta y consulta de inmuebles. El estado de pago lo decide {@link EstadoPagoPolicy}. */
@Service
public class InmuebleServiceImpl implements InmuebleService {

    private final InmuebleRepository inmuebles;
    private final ParqueaderoRepository parqueaderos;
    private final UsuarioRepository usuarios;
    private final ZonaComunRepository zonas;
    private final LiquidacionRepository liquidaciones;
    private final EstadoPagoPolicy estadoPago;

    public InmuebleServiceImpl(InmuebleRepository inmuebles, ParqueaderoRepository parqueaderos,
                               UsuarioRepository usuarios, ZonaComunRepository zonas,
                               LiquidacionRepository liquidaciones, EstadoPagoPolicy estadoPago) {
        this.inmuebles = inmuebles;
        this.parqueaderos = parqueaderos;
        this.usuarios = usuarios;
        this.zonas = zonas;
        this.liquidaciones = liquidaciones;
        this.estadoPago = estadoPago;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public InmuebleResponses.Detalle crear(InmuebleRequests.CrearInmueble solicitud) {
        String direccion = solicitud.direccionInmueble().trim().toUpperCase(Locale.ROOT);
        if (inmuebles.existePorDireccion(direccion)) {
            throw new ConflictoException("Ya existe el inmueble " + direccion);
        }
        Long id = inmuebles.insertar(direccion);
        if (solicitud.valorParqueadero() != null && solicitud.valorParqueadero().signum() > 0) {
            parqueaderos.insertar(id, solicitud.valorParqueadero());
        }
        inmuebles.asignarZonasActivas(id); // un inmueble nuevo puede usar las zonas comunes activas
        return detalle(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(NombresCache.INMUEBLES)
    public Pagina<InmuebleResponses.Resumen> listar(SolicitudPagina pagina) {
        List<InmuebleResponses.Resumen> filas = inmuebles.listarResumen(pagina.limite(), pagina.desplazamiento())
                .stream().map(this::resumen).toList();
        return Pagina.de(filas, inmuebles.contar(), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    public InmuebleResponses.Detalle detalle(Long inmuebleId) {
        InmuebleBasicoView base = inmuebles.buscarBasico(inmuebleId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe"));
        InmuebleResumenView resumenVista = inmuebles.buscarResumen(inmuebleId).orElseThrow();
        List<UsuarioListaView> usuariosDelInmueble = usuarios.listarPorInmueble(inmuebleId);

        // Inmueble.mostrarUsuarios(...) devuelve los nombres: la regla vive en el dominio.
        Inmueble dominio = Hidratador.inmueble(base, usuariosDelInmueble, Optional.empty());
        return InmuebleMapper.detalle(
                resumen(resumenVista),
                dominio.mostrarUsuarios(null),
                usuariosDelInmueble.stream().map(UsuarioMapper::de).toList(),
                zonas.listarActivasDeInmueble(inmuebleId).stream().map(ZonaMapper::zona).toList(),
                liquidaciones.buscarVigenteDeInmueble(inmuebleId).map(LiquidacionMapper::de).orElse(null));
    }

    private InmuebleResponses.Resumen resumen(InmuebleResumenView v) {
        String estado = estadoPago.calcular(v.getTotalLiquidaciones(), v.getSaldoPendiente(),
                v.getFechaLimitePendiente(), Boolean.TRUE.equals(v.getTieneMora()));
        return InmuebleMapper.resumen(v, estado);
    }
}
