package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.response.ReglaResponses;
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
import java.time.LocalDate;
import java.util.List;

/** Reglas de reserva: la validacion de valores la hace {@link ReglaReserva}, la activacion {@link Rompimiento}. */
@Service
public class ReglaReservaServiceImpl implements ReglaReservaService {

    private final ReglaReservaRepository reglas;
    private final RompimientoRepository rompimientos;
    private final ZonaComunRepository zonas;

    public ReglaReservaServiceImpl(ReglaReservaRepository reglas, RompimientoRepository rompimientos,
                                   ZonaComunRepository zonas) {
        this.reglas = reglas;
        this.rompimientos = rompimientos;
        this.zonas = zonas;
    }

    @Override
    @Transactional
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
                    Hidratador.referenciaRegla(reglaId), LocalDate.now(), s.observaciones());
            rompimiento.aplicarRegla();
            rompimientos.insertar(rompimiento);
        }
        return respuesta(reglaId);
    }

    @Override
    @Transactional
    public ReglaResponses.Regla actualizarReglas(Long reglaId, ReglaRequests.ActualizarReglas s) {
        ReglaReserva regla = reglas.buscarPorId(reglaId).map(Hidratador::regla)
                .orElseThrow(() -> new RecursoNoEncontradoException("La regla " + reglaId + " no existe"));
        regla.actualizarReglas(s.anticipacionMinimaHoras(), s.duracionMaximaHoras());
        reglas.actualizarReglas(reglaId, regla.getAnticipacionMinimaHoras(), regla.getDuracionMaximaHoras());
        return respuesta(reglaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglaResponses.Regla> listar() {
        return reglas.listar().stream()
                .map(r -> ZonaMapper.regla(r, rompimientos.listarPorRegla(r.getId()))).toList();
    }

    @Override
    @Transactional
    public ReglaResponses.Rompimiento aplicarRegla(Long rompimientoId) {
        Rompimiento rompimiento = cargar(rompimientoId);
        rompimiento.aplicarRegla();
        rompimientos.actualizarActiva(rompimientoId, rompimiento.getActiva(), rompimiento.getFechaAplicacion());
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(rompimientoId).orElseThrow());
    }

    @Override
    @Transactional
    public ReglaResponses.Rompimiento inactivarRegla(Long rompimientoId) {
        Rompimiento rompimiento = cargar(rompimientoId);
        rompimiento.inactivarRegla();
        rompimientos.actualizarActiva(rompimientoId, rompimiento.getActiva(), rompimiento.getFechaAplicacion());
        return ZonaMapper.rompimiento(rompimientos.buscarPorId(rompimientoId).orElseThrow());
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
