package unisimon.demo.service;

import unisimon.demo.dto.request.ReglaRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReglaResponses;

/** Reglas de reserva y su aplicacion a las zonas comunes. */
public interface ReglaReservaService {

    /** Administrador.ConfigurarNuevaReglaReserva(inmueble, adminId): crea la regla y la aplica a las zonas indicadas. */
    ReglaResponses.Regla configurarNuevaReglaReserva(Long adminId, ReglaRequests.CrearRegla solicitud);

    /** ReglaReserva.actualizarReglas(nuevaAnticipacion, nuevaDuracion) */
    ReglaResponses.Regla actualizarReglas(Long reglaId, ReglaRequests.ActualizarReglas solicitud);

    Pagina<ReglaResponses.Regla> listar(SolicitudPagina pagina);

    /** Rompimiento.aplicarRegla() */
    ReglaResponses.Rompimiento aplicarRegla(Long rompimientoId);

    /** Rompimiento.inactivarRegla() */
    ReglaResponses.Rompimiento inactivarRegla(Long rompimientoId);

    ReglaResponses.Regla consultar(Long reglaId);

    /** Solo si la regla ya no esta aplicada a ninguna zona. */
    void eliminar(Long reglaId);

    /** Crea el Rompimiento: aplica la regla a otra zona comun. */
    ReglaResponses.Rompimiento asignarZona(Long reglaId, ReglaRequests.AsignarZona solicitud);

    ReglaResponses.Rompimiento consultarRompimiento(Long rompimientoId);

    void eliminarRompimiento(Long rompimientoId);
}
