package unisimon.demo.service;

import unisimon.demo.dto.response.InmuebleResponses;

import java.time.LocalDate;

/** Totales del panel del administrador (recaudo, por cobrar, apartamentos por estado). */
public interface PanelAdministradorService {

    /** periodo: cualquier dia del mes que se quiere ver; null = el mes actual. */
    InmuebleResponses.ResumenAdmin resumen(LocalDate periodo);
}
