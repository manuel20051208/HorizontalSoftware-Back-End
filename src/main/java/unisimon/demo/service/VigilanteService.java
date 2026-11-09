package unisimon.demo.service;

import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.VigilanteResponses;

import java.util.List;

/** Operaciones de la porteria. */
public interface VigilanteService {

    /** Vigilante.consultarReservas(): todas las reservas, marcando cuales estan en curso ahora. */
    List<ReservaResponses.Reserva> consultarReservas();

    /** Vigilante.verificarAccesoZonaComun(usuario): puede esta persona entrar ahora a una zona comun? */
    VigilanteResponses.Acceso verificarAccesoZonaComun(Long identificacion);

    /** Vigilante.existeInvitado(identificacion) */
    VigilanteResponses.InvitadoConsulta existeInvitado(Long identificacion);

    /** Registra el ingreso de un invitado vigente y avisa a los residentes que lo autorizaron. */
    VigilanteResponses.Ingreso registrarIngreso(Long identificacion);
}
