package unisimon.demo.service;

import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.response.ReglaResponses;

import java.util.List;

/** Reglas de reserva y su aplicacion a las zonas comunes. */
public interface ReglaReservaService {

    /** Administrador.ConfigurarNuevaReglaReserva(inmueble, adminId): crea la regla y la aplica a las zonas indicadas. */
    ReglaResponses.Regla configurarNuevaReglaReserva(Long adminId, ReglaRequests.CrearRegla solicitud);

    /** ReglaReserva.actualizarReglas(nuevaAnticipacion, nuevaDuracion) */
    ReglaResponses.Regla actualizarReglas(Long reglaId, ReglaRequests.ActualizarReglas solicitud);

    List<ReglaResponses.Regla> listar();

    /** Rompimiento.aplicarRegla() */
    ReglaResponses.Rompimiento aplicarRegla(Long rompimientoId);

    /** Rompimiento.inactivarRegla() */
    ReglaResponses.Rompimiento inactivarRegla(Long rompimientoId);
}
