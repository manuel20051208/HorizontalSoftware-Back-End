package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.request.AutoRequests;
import unisimon.demo.dto.response.AutoResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.InvitadoMapper;
import unisimon.demo.model.Auto;
import unisimon.demo.model.Parqueadero;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.service.AutoService;

/** Autos de los residentes (uno por residente) y consulta de duenos. */
@Service
public class AutoServiceImpl implements AutoService {

    private final AutoRepository autos;
    private final ParqueaderoRepository parqueaderos;
    private final ResidenteRepository residentes;
    private final InmuebleRepository inmuebles;

    public AutoServiceImpl(AutoRepository autos, ParqueaderoRepository parqueaderos, ResidenteRepository residentes,
                           InmuebleRepository inmuebles) {
        this.autos = autos;
        this.parqueaderos = parqueaderos;
        this.residentes = residentes;
        this.inmuebles = inmuebles;
    }

    @Override
    @Transactional
    public AutoResponses.Auto registrarAuto(Long residenteId, AutoRequests.RegistrarAuto s) {
        ContextoResidenteView residente = contexto(residenteId);
        if (autos.buscarPorDueno(residenteId).isPresent()) {
            throw new ConflictoException("Ya tiene un auto registrado");
        }
        if (autos.existePorPlaca(s.placa())) {
            throw new ConflictoException("Ya existe un auto con esa placa");
        }
        Parqueadero parqueadero = null;
        if (s.parqueaderoId() != null) {
            ParqueaderoView vista = parqueaderos.buscarPorId(s.parqueaderoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("El parqueadero " + s.parqueaderoId() + " no existe"));
            if (!vista.getInmuebleId().equals(residente.getInmuebleId())) {
                throw new ProhibidoException("Ese parqueadero pertenece a otro inmueble");
            }
            if (autos.existePorParqueadero(vista.getId())) {
                throw new ConflictoException("Ese parqueadero ya tiene un auto asignado");
            }
            parqueadero = new Parqueadero(vista.getId(), null, vista.getValorMensual());
        }
        Auto auto = new Auto(null, Hidratador.referenciaResidente(residenteId), s.placa(), parqueadero);
        Long id = autos.insertar(auto);
        return InvitadoMapper.auto(autos.buscarPorId(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public AutoResponses.Auto consultarAuto(Long residenteId) {
        return autos.buscarPorDueno(residenteId).map(InvitadoMapper::auto)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tiene un auto registrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public AutoResponses.Duenos consultarDuenos(Long inmuebleId, Long autoId) {
        if (!inmuebles.existePorId(inmuebleId)) {
            throw new RecursoNoEncontradoException("El inmueble " + inmuebleId + " no existe");
        }
        return new AutoResponses.Duenos(autoId, inmuebleId,
                autos.listarDuenos(inmuebleId, autoId).stream().map(InvitadoMapper::dueno).toList());
    }

    private ContextoResidenteView contexto(Long residenteId) {
        return residentes.buscarContexto(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"));
    }
}
