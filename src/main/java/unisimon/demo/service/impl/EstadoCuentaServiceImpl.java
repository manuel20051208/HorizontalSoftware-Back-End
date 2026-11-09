package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.InmuebleMapper;
import unisimon.demo.mapper.LiquidacionMapper;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.Residente;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.LiquidacionRepository;
import unisimon.demo.repository.ResidenteRepository;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.service.EstadoCuentaService;

/** Estado de cuenta del residente: saldo, estado de pago y paz y salvo. */
@Service
public class EstadoCuentaServiceImpl implements EstadoCuentaService {

    private final ResidenteRepository residentes;
    private final InmuebleRepository inmuebles;
    private final LiquidacionRepository liquidaciones;
    private final EstadoPagoPolicy estadoPago;

    public EstadoCuentaServiceImpl(ResidenteRepository residentes, InmuebleRepository inmuebles,
                                   LiquidacionRepository liquidaciones, EstadoPagoPolicy estadoPago) {
        this.residentes = residentes;
        this.inmuebles = inmuebles;
        this.liquidaciones = liquidaciones;
        this.estadoPago = estadoPago;
    }

    @Override
    @Transactional(readOnly = true)
    public InmuebleResponses.EstadoCuenta consultarEstadoCuenta(Long residenteId) {
        ContextoResidenteView contexto = residentes.buscarContexto(residenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El residente no tiene un inmueble asignado"));
        InmuebleResumenView resumen = inmuebles.buscarResumen(contexto.getInmuebleId()).orElseThrow();
        String estado = estadoPago.calcular(resumen.getTotalLiquidaciones(), resumen.getSaldoPendiente(),
                resumen.getFechaLimitePendiente(), Boolean.TRUE.equals(resumen.getTieneMora()));

        // Inmueble.pagoAlDiaAdministracion(liquidacion): la regla de "al dia" vive en el dominio.
        Residente dominio = Hidratador.residente(contexto);
        Inmueble inmueble = dominio.getInmueble();
        boolean sinDeuda = resumen.getSaldoPendiente().signum() == 0;
        boolean pazYSalvo = sinDeuda
                && (inmueble.getLiquidacion() == null || inmueble.pagoAlDiaAdministracion(inmueble.getLiquidacion()));

        return InmuebleMapper.estadoCuenta(InmuebleMapper.resumen(resumen, estado), pazYSalvo,
                liquidaciones.buscarVigenteDeInmueble(contexto.getInmuebleId()).map(LiquidacionMapper::de).orElse(null));
    }
}
