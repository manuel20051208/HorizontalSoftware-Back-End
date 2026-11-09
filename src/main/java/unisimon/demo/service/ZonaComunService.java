package unisimon.demo.service;

import unisimon.demo.dto.request.ZonaRequests;
import unisimon.demo.dto.response.ZonaResponses;

import java.util.List;

/** Zonas comunes y su asignacion a los inmuebles. */
public interface ZonaComunService {

    ZonaResponses.Zona crearZona(ZonaRequests.CrearZona solicitud);

    List<ZonaResponses.Zona> listar();

    /** ReservaZonaComun.mostrarZonasComunes(): zonas activas asignadas al inmueble. */
    List<ZonaResponses.Zona> mostrarZonasComunes(Long inmuebleId);

    /** ZonaComun.cambiarEstado(): activa o desactiva la zona. */
    ZonaResponses.Zona cambiarEstado(Long zonaId);

    /** Administrador.ConfigurarZonasComunes(inmueble, adminId): define que zonas puede usar un inmueble. */
    List<ZonaResponses.Zona> configurarZonasComunes(Long inmuebleId, List<Long> zonaComunIds);
}
