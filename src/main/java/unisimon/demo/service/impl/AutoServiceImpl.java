package unisimon.demo.service.impl;

import lombok.RequiredArgsConstructor;
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
import unisimon.demo.model.Paqueadero;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.AutoRepository.AutoView;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.ParqueaderoRepository;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;
import unisimon.demo.service.AutoService;
import unisimon.demo.service.support.InmuebleDelResidente;

/** Autos de los residentes (uno por residente) y consulta de duenos. */
@Service
@RequiredArgsConstructor
public class AutoServiceImpl implements AutoService {

    private final AutoRepository autos;
    private final ParqueaderoRepository parqueaderos;
    private final InmuebleDelResidente inmuebleDelResidente;
    private final InmuebleRepository inmuebles;

    @Override
    @Transactional
    public AutoResponses.Auto registrarAuto(Long residenteId, Long inmuebleId, AutoRequests.RegistrarAuto s) {
        Long elegido = inmuebleDelResidente.resolver(residenteId, inmuebleId).getId();
        if (autos.buscarPorDueno(residenteId).isPresent()) {
            throw new ConflictoException("Ya tiene un auto registrado");
        }
        if (autos.existePorPlaca(s.placa())) {
            throw new ConflictoException("Ya existe un auto con esa placa");
        }
        Paqueadero parqueadero = null;
        if (s.parqueaderoId() != null) {
            ParqueaderoView vista = parqueaderos.buscarPorId(s.parqueaderoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("El parqueadero " + s.parqueaderoId() + " no existe"));
            if (!vista.getInmuebleId().equals(elegido)) {
                throw new ProhibidoException("Ese parqueadero pertenece a otro inmueble");
            }
            if (autos.existePorParqueadero(vista.getId())) {
                throw new ConflictoException("Ese parqueadero ya tiene un auto asignado");
            }
            parqueadero = new Paqueadero(vista.getId(), null, vista.getValorMensual());
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

    @Override
    @Transactional
    public AutoResponses.Auto actualizarAuto(Long residenteId, AutoRequests.ActualizarAuto s) {
        AutoView auto = autos.buscarPorDueno(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tiene un auto registrado"));
        if (autos.existePorPlacaEnOtro(s.placa(), auto.getId())) {
            throw new ConflictoException("Ya existe un auto con esa placa");
        }
        Long parqueaderoId = s.parqueaderoId();
        if (parqueaderoId != null && !parqueaderoId.equals(auto.getParqueaderoId())) {
            ParqueaderoView vista = parqueaderos.buscarPorId(parqueaderoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("El parqueadero " + parqueaderoId + " no existe"));
            boolean esDeSuInmueble = inmuebleDelResidente.listar(residenteId).stream()
                    .anyMatch(i -> i.getId().equals(vista.getInmuebleId()));
            if (!esDeSuInmueble) {
                throw new ProhibidoException("Ese parqueadero pertenece a otro inmueble");
            }
            if (autos.existePorParqueadero(parqueaderoId)) {
                throw new ConflictoException("Ese parqueadero ya tiene un auto asignado");
            }
        }
        autos.actualizarDatos(auto.getId(), s.placa(), parqueaderoId);
        return InvitadoMapper.auto(autos.buscarPorId(auto.getId()).orElseThrow());
    }

    /** Residente ◆— Auto es composicion: el auto es parte del residente y se borra solo; el parqueadero queda libre. */
    @Override
    @Transactional
    public void eliminarAuto(Long residenteId) {
        AutoView auto = autos.buscarPorDueno(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tiene un auto registrado"));
        autos.eliminar(auto.getId());
    }
}
