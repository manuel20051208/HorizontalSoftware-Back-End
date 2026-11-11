package unisimon.demo.service;

import unisimon.demo.dto.request.InvitadoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;

import java.util.List;

/** Invitados (UsuarioAutorizado) de un residente. */
public interface InvitadoService {

    InvitadoResponses.Invitado crearInvitado(Long residenteId, InvitadoRequests.CrearInvitado solicitud);

    Pagina<InvitadoResponses.Invitado> listarInvitados(Long residenteId, SolicitudPagina pagina);

    /** Residente.cambiarEstadoInvitado(invitado, id): invierte el estado de un invitado propio. */
    InvitadoResponses.CambioEstado cambiarEstadoInvitado(Long residenteId, Long invitadoId);

    /** Residente.cambiarEstadoInvitados(invitados): invierte el estado de varios invitados propios. */
    InvitadoResponses.CambioEstadoMasivo cambiarEstadoInvitados(Long residenteId, List<Long> invitadoIds);

    /** UsuarioAutorizado.buscarRelacionResidente(): residentes que autorizaron a este invitado. */
    Pagina<InvitadoResponses.Anfitrion> buscarRelacionResidente(Long invitadoId, SolicitudPagina pagina);
}
