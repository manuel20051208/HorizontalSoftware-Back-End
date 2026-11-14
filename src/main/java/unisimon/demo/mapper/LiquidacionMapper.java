package unisimon.demo.mapper;

import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.repository.LiquidacionRepository.LiquidacionView;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

/** LiquidacionView -> respuesta. "vencida" se pregunta al dominio (Liquidacion.estaVencida). */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiquidacionMapper {

    public static LiquidacionResponses.Liquidacion de(LiquidacionView v) {
        Liquidacion dominio = Hidratador.liquidacion(v);
        return new LiquidacionResponses.Liquidacion(v.getId(), v.getInmuebleId(), v.getDireccionInmueble(),
                v.getPeriodo(), v.getFechaGeneracion(), v.getFechaLimite(), v.getCuotaAdministracion(),
                v.getValorServiciosComunes(), v.getValorMultas(), v.getTotal(), v.getEstado(),
                dominio.estaVencida());
    }
}
