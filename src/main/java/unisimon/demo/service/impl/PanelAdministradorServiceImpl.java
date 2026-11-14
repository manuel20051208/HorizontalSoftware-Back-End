package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.ReporteResponses;
import unisimon.demo.mapper.InmuebleMapper;
import unisimon.demo.model.Pago;
import unisimon.demo.policy.EstadoPagoPolicy;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;
import unisimon.demo.repository.PagoRepository;
import unisimon.demo.repository.PagoRepository.TotalInmuebleView;
import unisimon.demo.service.PanelAdministradorService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public ReporteResponses.Consolidado generarReportes() {
        // Lo aprobado de cada inmueble (Pago.estaAprobado) y lo que aun debe (liquidaciones sin pagar).
        Map<Long, BigDecimal> aprobado = pagos.sumarPorInmueble(Pago.APROBADO).stream()
                .collect(Collectors.toMap(TotalInmuebleView::getInmuebleId, TotalInmuebleView::getTotal));

        List<ReporteResponses.Inmueble> filas = new ArrayList<>();
        BigDecimal recaudo = BigDecimal.ZERO;
        BigDecimal mora = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        int alDia = 0;
        int enMora = 0;
        int pendientes = 0;
        for (InmuebleResumenView fila : inmuebles.listarResumen()) {
            String estado = estadoPago.calcular(fila.getTotalLiquidaciones(), fila.getSaldoPendiente(),
                    fila.getFechaLimitePendiente(), Boolean.TRUE.equals(fila.getTieneMora()));
            BigDecimal recaudado = aprobado.getOrDefault(fila.getId(), BigDecimal.ZERO);
            BigDecimal saldo = fila.getSaldoPendiente();
            recaudo = recaudo.add(recaudado);
            switch (estado) {
                case EstadoPagoPolicy.VENCIDA -> {
                    enMora++;
                    mora = mora.add(saldo);
                }
                case EstadoPagoPolicy.POR_VENCER -> {
                    pendientes++;
                    pendiente = pendiente.add(saldo);
                }
                case EstadoPagoPolicy.AL_DIA -> alDia++;
                default -> {
                    // SIN_LIQUIDACION: no suma a ningun total
                }
            }
            filas.add(new ReporteResponses.Inmueble(fila.getId(), fila.getDireccionInmueble(), estado, recaudado,
                    saldo));
        }
        return new ReporteResponses.Consolidado(LocalDate.now(reloj), filas.size(), alDia, enMora, pendientes,
                recaudo, mora, pendiente, filas);
    }
}
