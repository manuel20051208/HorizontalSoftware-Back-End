package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.cache.NombresCache;
import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReglaResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.ZonaMapper;
import unisimon.demo.model.ReglaReserva;
import unisimon.demo.model.Rompimiento;
import unisimon.demo.repository.ReglaReservaRepository;
import unisimon.demo.repository.ReglaReservaRepository.ReglaView;
import unisimon.demo.repository.RompimientoRepository;
import unisimon.demo.repository.RompimientoRepository.RompimientoView;
import unisimon.demo.repository.ZonaComunRepository;
import unisimon.demo.service.ReglaReservaService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Reglas de reserva: la validacion de valores la hace {@link ReglaReserva}, la activacion {@link Rompimiento}. */
@Service
@RequiredArgsConstructor
public class ReglaReservaServiceImpl implements ReglaReservaService {

    private final ReglaReservaRepository reglas;
    private final RompimientoRepository rompimientos;
    private final ZonaComunRepository zonas;
    private final Clock reloj;

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public ReglaResponses.Regla configurarNuevaReglaReserva(Long adminId, ReglaRequests.CrearRegla s) {
        List<Long> zonaIds = s.zonaComunIds().stream().distinct().toList();
        if (zonas.contarPorIds(zonaIds) != zonaIds.size()) {
            throw new RecursoNoEncontradoException("Alguna de las zonas comunes indicadas no existe");
        }
        ReglaReserva regla = new ReglaReserva(null, s.anticipacionMinimaHoras(), s.duracionMaximaHoras(),
                s.horasAntelacionPenalidad() == null ? 0 : s.horasAntelacionPenalidad(),
                s.tarifaPenalidad() == null ? BigDecimal.ZERO : s.tarifaPenalidad(),
                Hidratador.referenciaAdministrador(adminId));
        regla.actualizarReglas(s.anticipacionMinimaHoras(), s.duracionMaximaHoras());   // valida los valores
        Long reglaId = reglas.insertar(regla);
        for (Long zonaId : zonaIds) {
            Rompimiento rompimiento = new Rompimiento(null, Hidratador.referenciaZona(zonaId),
                    Hidratador.referenciaRegla(reglaId), LocalDate.now(reloj), s.observaciones());
            rompimiento.aplicarRegla();
            rompimientos.insertar(rompimiento);
        }
        return respuesta(reglaId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public ReglaResponses.Regla actualizarReglas(Long reglaId, ReglaRequests.ActualizarReglas s) {
        ReglaReserva regla = reglas.buscarPorId(reglaId).map(Hidratador::regla)
                .orElseThrow(() -> new RecursoNoEncontradoException("La regla " + reglaId + " no existe"));
        regla.actualizarReglas(s.anticipacionMinimaHoras(), s.duracionMaximaHoras());
        reglas.actualizarReglas(reglaId, regla.getAnticipacionMinimaHoras(), regla.getDuracionMaximaHoras());
        return respuesta(reglaId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(NombresCache.REGLAS_RESERVA)
    public Pagina<ReglaResponses.Regla> listar(SolicitudPagina pagina) {
        List<ReglaResponses.Regla> filas = reglas.listar(pagina.limite(), pagina.desplazamiento()).stream()
                .map(r -> ZonaMapper.regla(r, rompimientos.listarPorRegla(r.getId()))).toList();
        return Pagina.de(filas, reglas.contar(), pagina);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public ReglaResponses.Rompimiento aplicarRegla(Long rompimientoId) {
        Rompimiento rompimiento = cargar(rompimientoId);
        rompimiento.aplicarRegla();
        rompimientos.actualizarActiva(rompimientoId, rompimiento.getActiva(), rompimiento.getFechaAplicacion());
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(rompimientoId).orElseThrow());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public ReglaResponses.Rompimiento inactivarRegla(Long rompimientoId) {
        Rompimiento rompimiento = cargar(rompimientoId);
        rompimiento.inactivarRegla();
        rompimientos.actualizarActiva(rompimientoId, rompimiento.getActiva(), rompimiento.getFechaAplicacion());
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(rompimientoId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ReglaResponses.Regla consultar(Long reglaId) {
        existente(reglaId);
        return respuesta(reglaId);
    }

    /** Rompimiento -> ReglaReserva es asociacion obligatoria (1): con asignaciones no se borra la regla (409). */
    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public void eliminar(Long reglaId) {
        existente(reglaId);
        if (rompimientos.contarPorRegla(reglaId) > 0) {
            throw new ConflictoException("La regla sigue aplicada a zonas comunes; quite esas asignaciones primero");
        }
        reglas.eliminar(reglaId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public ReglaResponses.Rompimiento asignarZona(Long reglaId, ReglaRequests.AsignarZona s) {
        existente(reglaId);
        if (zonas.buscarPorId(s.zonaComunId()).isEmpty()) {
            throw new RecursoNoEncontradoException("La zona comun " + s.zonaComunId() + " no existe");
        }
        if (rompimientos.existePorZonaYRegla(s.zonaComunId(), reglaId)) {
            throw new ConflictoException("La regla ya esta aplicada a esa zona comun");
        }
        Rompimiento rompimiento = new Rompimiento(null, Hidratador.referenciaZona(s.zonaComunId()),
                Hidratador.referenciaRegla(reglaId), LocalDate.now(reloj), s.observaciones());
        rompimiento.aplicarRegla();
        Long id = rompimientos.insertar(rompimiento);
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ReglaResponses.Rompimiento consultarRompimiento(Long rompimientoId) {
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(rompimientoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La asignacion " + rompimientoId + " no existe")));
    }

    /** ZonaComun ◇— Rompimiento es agregacion: borrar la asignacion no borra la zona ni la regla. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = NombresCache.REGLAS_RESERVA, allEntries = true)
    public void eliminarRompimiento(Long rompimientoId) {
        cargar(rompimientoId);
        rompimientos.eliminar(rompimientoId);
    }

    private void existente(Long reglaId) {
        if (reglas.buscarPorId(reglaId).isEmpty()) {
            throw new RecursoNoEncontradoException("La regla " + reglaId + " no existe");
        }
    }

    private Rompimiento cargar(Long rompimientoId) {
        return rompimientos.buscarPorId(rompimientoId).map(Hidratador::rompimiento)
                .orElseThrow(() -> new RecursoNoEncontradoException("La asignacion " + rompimientoId + " no existe"));
    }

    private ReglaResponses.Regla respuesta(Long reglaId) {
        ReglaView vista = reglas.buscarPorId(reglaId).orElseThrow();
        List<RompimientoView> zonasDeLaRegla = rompimientos.listarPorRegla(reglaId);
        return ZonaMapper.regla(vista, zonasDeLaRegla);
    }
}
