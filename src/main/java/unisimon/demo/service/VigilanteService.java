package unisimon.demo.service;

import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.VigilanteResponses;

/** Operaciones de la porteria. */
public interface VigilanteService {

    /** Vigilante.consultarReservas(): todas las reservas con su estado, opcionalmente solo las de un estado. */
    Pagina<ReservaResponses.Reserva> consultarReservas(String estado, SolicitudPagina pagina);

    /** Vigilante.verificarAccesoZonaComun(usuario): tiene esta persona una reserva en curso de una zona activa? */
    VigilanteResponses.Acceso verificarAccesoZonaComun(Long identificacion);

    /** Vigilante.existeInvitado(identificacion) */
    VigilanteResponses.InvitadoConsulta existeInvitado(Long identificacion);

    /** Registra el ingreso de un invitado vigente y avisa a los residentes que lo autorizaron. */
    VigilanteResponses.Ingreso registrarIngreso(Long identificacion);
}
