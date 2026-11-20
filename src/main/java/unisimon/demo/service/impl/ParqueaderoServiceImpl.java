package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.ParqueaderoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ParqueaderoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.ParqueaderoMapper;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;
import unisimon.demo.service.ParqueaderoService;

import java.util.List;

/** Parqueaderos. Su valor mensual sale en el listado de inmuebles, por eso se limpia ese cache. */
@Service
@RequiredArgsConstructor
public class ParqueaderoServiceImpl implements ParqueaderoService {

    private final ParqueaderoRepository parqueaderos;
    private final InmuebleRepository inmuebles;
    private final AutoRepository autos;

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public ParqueaderoResponses.Parqueadero crear(ParqueaderoRequests.CrearParqueadero s) {
        if (!inmuebles.existePorId(s.inmuebleId())) {
            throw new RecursoNoEncontradoException("El inmueble " + s.inmuebleId() + " no existe");
        }
        if (parqueaderos.buscarPorInmueble(s.inmuebleId()).isPresent()) {
            throw new ConflictoException("El inmueble " + s.inmuebleId() + " ya tiene parqueadero");
        }
        Long id = parqueaderos.insertar(s.inmuebleId(), s.valorMensual());
        return ParqueaderoMapper.de(parqueaderos.buscarPorId(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<ParqueaderoResponses.Parqueadero> listar(SolicitudPagina pagina) {
        List<ParqueaderoResponses.Parqueadero> filas = parqueaderos.listar(pagina.limite(), pagina.desplazamiento())
                .stream().map(ParqueaderoMapper::de).toList();
        return Pagina.de(filas, parqueaderos.contar(), pagina);
    }

    @Override
    @Transactional(readOnly = true)
    public ParqueaderoResponses.Parqueadero consultar(Long parqueaderoId) {
        return ParqueaderoMapper.de(cargar(parqueaderoId));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public ParqueaderoResponses.Parqueadero actualizar(Long parqueaderoId,
                                                       ParqueaderoRequests.ActualizarParqueadero s) {
        cargar(parqueaderoId);
        parqueaderos.actualizarValorMensualPorId(parqueaderoId, s.valorMensual());
        return ParqueaderoMapper.de(parqueaderos.buscarPorId(parqueaderoId).orElseThrow());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.INMUEBLES, allEntries = true)
    public void eliminar(Long parqueaderoId) {
        cargar(parqueaderoId);
        autos.liberarParqueadero(parqueaderoId);   // asociacion: el auto sigue existiendo
        parqueaderos.eliminar(parqueaderoId);
    }

    private ParqueaderoView cargar(Long parqueaderoId) {
        return parqueaderos.buscarPorId(parqueaderoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El parqueadero " + parqueaderoId + " no existe"));
    }
}
