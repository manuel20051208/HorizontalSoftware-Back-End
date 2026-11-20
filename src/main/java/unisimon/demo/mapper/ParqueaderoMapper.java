package unisimon.demo.mapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import unisimon.demo.dto.response.ParqueaderoResponses;
import unisimon.demo.model.Paqueadero;
import unisimon.demo.repository.ParqueaderoRepository.ParqueaderoView;

/** ParqueaderoView -> respuesta. El valor mensual se pregunta al dominio (Paqueadero.obtenerValorMensual). */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParqueaderoMapper {

    public static ParqueaderoResponses.Parqueadero de(ParqueaderoView v) {
        Paqueadero dominio = new Paqueadero(v.getId(), null, v.getValorMensual());
        return new ParqueaderoResponses.Parqueadero(v.getId(), v.getInmuebleId(), dominio.obtenerValorMensual());
    }
}
