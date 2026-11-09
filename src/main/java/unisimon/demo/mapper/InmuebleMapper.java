package unisimon.demo.mapper;

import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.repository.InmuebleRepository.InmuebleResumenView;

import java.math.BigDecimal;
import java.util.List;

/** Proyecciones de inmuebles -> respuestas del API. El estado de pago lo decide EstadoPagoPolicy, no el mapper. */
public final class InmuebleMapper {

    private InmuebleMapper() {
    }

    public static InmuebleResponses.Resumen resumen(InmuebleResumenView v, String estadoPago) {
        return new InmuebleResponses.Resumen(v.getId(), v.getDireccionInmueble(), v.getIsActive(), estadoPago,
                v.getSaldoPendiente(), v.getFechaLimitePendiente(), v.getValorParqueadero(),
                v.getTotalLiquidaciones());
    }

    public static InmuebleResponses.Detalle detalle(InmuebleResponses.Resumen r, List<String> nombres,
                                                    List<UsuarioResponses.Usuario> usuarios,
                                                    List<ZonaResponses.Zona> zonas,
                                                    LiquidacionResponses.Liquidacion vigente) {
        return new InmuebleResponses.Detalle(r.id(), r.direccionInmueble(), r.isActive(), r.estadoPago(),
                r.saldoPendiente(), r.fechaLimitePendiente(), r.valorParqueadero(), nombres, usuarios, zonas, vigente);
    }

    public static InmuebleResponses.EstadoCuenta estadoCuenta(InmuebleResponses.Resumen r, boolean pazYSalvo,
                                                              LiquidacionResponses.Liquidacion vigente) {
        return new InmuebleResponses.EstadoCuenta(r.id(), r.direccionInmueble(), r.estadoPago(),
                r.saldoPendiente(), pazYSalvo, vigente);
    }

    public static InmuebleResponses.ResumenAdmin resumenAdmin(java.time.LocalDate periodo, int total, int alDia,
                                                              int porVencer, int vencidas, int sinLiquidacion,
                                                              BigDecimal porCobrar, BigDecimal recaudoMes) {
        return new InmuebleResponses.ResumenAdmin(periodo, total, alDia, porVencer, vencidas, sinLiquidacion,
                porCobrar, recaudoMes);
    }
}
