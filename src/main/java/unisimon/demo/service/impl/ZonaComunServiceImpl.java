package unisimon.demo.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.ZonaRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.ZonaMapper;
import unisimon.demo.model.ZonaComun;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.service.ZonaComunService;

import java.util.List;

/** Zonas comunes. Activar/desactivar lo decide la entidad {@link ZonaComun}; aqui solo se orquesta y se guarda. */
@Service
public class ZonaComunServiceImpl implements ZonaComunService {

    private final ZonaComunRepository zonas;
    private final InmuebleRepository inmuebles;

    public ZonaComunServiceImpl(ZonaComunRepository zonas, InmuebleRepository inmuebles) {
        this.zonas = zonas;
        this.inmuebles = inmuebles;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public ZonaResponses.Zona crearZona(ZonaRequests.CrearZona s) {
        String nombre = s.nombreZonaComun().trim();
        if (s.horaInicio().equals(s.horaFin())) {
            throw new SolicitudInvalidaException("horaInicio y horaFin no pueden ser iguales");
        }
        if (zonas.existePorNombre(nombre)) {
            throw new ConflictoException("Ya existe la zona comun " + nombre);
        }
        Long id = zonas.insertar(new ZonaComun(null, nombre, s.capacidad(), s.horaInicio(), s.horaFin()));
        if (!Boolean.FALSE.equals(s.aplicarATodosLosInmuebles())) {
            inmuebles.asignarZonaATodos(id);
        }
        return ZonaMapper.zona(zonas.buscarPorId(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(NombresCache.ZONAS_COMUNES)
    public Pagina<ZonaResponses.Zona> listar(SolicitudPagina pagina) {
        List<ZonaResponses.Zona> filas = zonas.listar(pagina.limite(), pagina.desplazamiento()).stream()
                .map(ZonaMapper::zona).toList();
        return Pagina.de(filas, zonas.contar(), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(NombresCache.ZONAS_POR_INMUEBLE)
    public Pagina<ZonaResponses.Zona> mostrarZonasComunes(Long inmuebleId, SolicitudPagina pagina) {
        List<ZonaResponses.Zona> filas = zonas.listarActivasDeInmueble(inmuebleId, pagina.limite(),
                pagina.desplazamiento()).stream().map(ZonaMapper::zona).toList();
        return Pagina.de(filas, zonas.contarActivasDeInmueble(inmuebleId), pagina);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public ZonaResponses.Zona cambiarEstado(Long zonaId) {
        ZonaComun zona = zonas.buscarPorId(zonaId).map(Hidratador::zona)
                .orElseThrow(() -> new RecursoNoEncontradoException("La zona comun " + zonaId + " no existe"));
        zona.cambiarEstado();
        zonas.actualizarEstado(zonaId, zona.getIsActive());
        return ZonaMapper.zona(zonas.buscarPorId(zonaId).orElseThrow());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.ZONAS_COMUNES, NombresCache.ZONAS_POR_INMUEBLE}, allEntries = true)
    public List<ZonaResponses.Zona> configurarZonasComunes(Long inmuebleId, List<Long> zonaComunIds) {
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
        List<Long> ids = zonaComunIds.stream().distinct().toList();
        if (zonas.contarPorIds(ids) != ids.size()) {
            throw new RecursoNoEncontradoException("Alguna de las zonas comunes indicadas no existe");
        }
        inmuebles.limpiarZonas(inmuebleId);
        inmuebles.asignarZonas(inmuebleId, ids);
        return zonas.listarActivasDeInmueble(inmuebleId).stream().map(ZonaMapper::zona).toList();
    }
}
