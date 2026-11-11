package unisimon.demo.service;

import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.ZonaRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ZonaResponses;

import java.util.List;

/** Zonas comunes y su asignacion a los inmuebles. */
public interface ZonaComunService {

    ZonaResponses.Zona crearZona(ZonaRequests.CrearZona solicitud);

    Pagina<ZonaResponses.Zona> listar(SolicitudPagina pagina);

    /** ReservaZonaComun.mostrarZonasComunes(): zonas activas asignadas al inmueble. */
    Pagina<ZonaResponses.Zona> mostrarZonasComunes(Long inmuebleId, SolicitudPagina pagina);

    /** ZonaComun.cambiarEstado(): activa o desactiva la zona. */
    ZonaResponses.Zona cambiarEstado(Long zonaId);

    /** Administrador.ConfigurarZonasComunes(inmueble, adminId): define que zonas puede usar un inmueble. */
    List<ZonaResponses.Zona> configurarZonasComunes(Long inmuebleId, List<Long> zonaComunIds);
}
