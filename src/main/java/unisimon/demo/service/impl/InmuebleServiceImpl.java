package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
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
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.UsuarioDeInmuebleView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.service.InmuebleService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Alta y consulta de inmuebles. El estado de pago lo decide {@link EstadoPagoPolicy}. */
@Service
@RequiredArgsConstructor
public class InmuebleServiceImpl implements InmuebleService {

    private final InmuebleRepository inmuebles;
    private final ParqueaderoRepository parqueaderos;
    private final UsuarioRepository usuarios;
    private final ZonaComunRepository zonas;
    private final LiquidacionRepository liquidaciones;
    private final EstadoPagoPolicy estadoPago;
    private final AutoRepository autos;

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

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public InmuebleResponses.Detalle actualizar(Long inmuebleId, InmuebleRequests.ActualizarInmueble s) {
        existente(inmuebleId);
        String direccion = s.direccionInmueble().trim().toUpperCase(Locale.ROOT);
        if (inmuebles.existePorDireccionEnOtro(direccion, inmuebleId)) {
            throw new ConflictoException("Ya existe el inmueble " + direccion);
        }
        inmuebles.actualizarDatos(inmuebleId, direccion, s.isActive());
        return detalle(inmuebleId);
    }

    /**
     * Inmueble es el TODO de varias relaciones del diagrama:
     * composicion con Pago, Paqueadero y Liquidacion (la base los borra en cascada) y agregacion con Residente,
     * ZonaComun y Notificacion (solo se borra el vinculo; esos objetos siguen existiendo). ReservaZonaComun -> Inmueble
     * es asociacion: con reservas registradas no se borra (409) y se sugiere desactivarlo.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.INMUEBLES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public void eliminar(Long inmuebleId) {
        existente(inmuebleId);
        if (inmuebles.contarReservas(inmuebleId) > 0) {
            throw new ConflictoException("El inmueble tiene reservas registradas; desactivelo en lugar de borrarlo");
        }
        List<Long> relacionados = usuarios.listarUsuariosDeInmueble(inmuebleId).stream()
                .map(UsuarioDeInmuebleView::getId).toList();
        inmuebles.limpiarZonas(inmuebleId);                  // agregacion Inmueble - ZonaComun
        inmuebles.desvincularNotificaciones(inmuebleId);     // agregacion Inmueble - Notificacion
        autos.liberarParqueaderosDeInmueble(inmuebleId);     // asociacion Auto - Paqueadero: el auto se queda
        inmuebles.eliminar(inmuebleId);                      // composicion: pagos, parqueadero y liquidaciones
        for (Long usuarioId : relacionados) {
            if (usuarios.contarInmueblesDe(usuarioId) == 0) {
                usuarios.actualizarToken(usuarioId, null);   // sin inmueble no conserva la sesion
            }
        }
    }

    private void existente(Long inmuebleId) {
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
    }

    private InmuebleResponses.Resumen resumen(InmuebleResumenView v) {
        String estado = estadoPago.calcular(v.getTotalLiquidaciones(), v.getSaldoPendiente(),
                v.getFechaLimitePendiente(), Boolean.TRUE.equals(v.getTieneMora()));
        return InmuebleMapper.resumen(v, estado);
    }
}
