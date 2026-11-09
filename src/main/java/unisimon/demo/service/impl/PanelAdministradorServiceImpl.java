package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.mapper.InmuebleMapper;
import unisimon.demo.model.Pago;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.service.PanelAdministradorService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Totales del panel: cuenta inmuebles por estado de pago y suma lo que falta por cobrar y lo recaudado en el mes. */
@Service
public class PanelAdministradorServiceImpl implements PanelAdministradorService {

    private final InmuebleRepository inmuebles;
    private final PagoRepository pagos;
    private final EstadoPagoPolicy estadoPago;
    private final Clock reloj;

    public PanelAdministradorServiceImpl(InmuebleRepository inmuebles, PagoRepository pagos,
                                         EstadoPagoPolicy estadoPago, Clock reloj) {
        this.inmuebles = inmuebles;
        this.pagos = pagos;
        this.estadoPago = estadoPago;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public InmuebleResponses.ResumenAdmin resumen(LocalDate periodo) {
        LocalDate primerDia = (periodo != null ? periodo : LocalDate.now(reloj)).withDayOfMonth(1);
        LocalDate ultimoDia = primerDia.withDayOfMonth(primerDia.lengthOfMonth());

        List<InmuebleResumenView> filas = inmuebles.listarResumen();
        int alDia = 0;
        int porVencer = 0;
        int vencidas = 0;
        int sinLiquidacion = 0;
        BigDecimal porCobrar = BigDecimal.ZERO;
        for (InmuebleResumenView fila : filas) {
            porCobrar = porCobrar.add(fila.getSaldoPendiente());
            switch (estadoPago.calcular(fila.getTotalLiquidaciones(), fila.getSaldoPendiente(),
                    fila.getFechaLimitePendiente(), Boolean.TRUE.equals(fila.getTieneMora()))) {
                case EstadoPagoPolicy.AL_DIA -> alDia++;
                case EstadoPagoPolicy.POR_VENCER -> porVencer++;
                case EstadoPagoPolicy.VENCIDA -> vencidas++;
                default -> sinLiquidacion++;
            }
        }
        BigDecimal recaudo = pagos.sumarPorEstadoEntre(Pago.APROBADO, primerDia, ultimoDia);
        return InmuebleMapper.resumenAdmin(primerDia, filas.size(), alDia, porVencer, vencidas, sinLiquidacion,
                porCobrar, recaudo);
    }
}
