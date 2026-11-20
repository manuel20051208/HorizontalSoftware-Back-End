package unisimon.demo.service;

import unisimon.demo.dto.request.InvitadoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.Pagina;

import java.util.List;

/** Invitados (UsuarioAutorizado) de un residente. */
public interface InvitadoService {

    /** Crea el invitado en el inmueble elegido por el residente. */
    InvitadoResponses.Invitado crearInvitado(Long residenteId, Long inmuebleId,
                                             InvitadoRequests.CrearInvitado solicitud);

    /** Los invitados del residente en el inmueble elegido por {@code inmuebleId} (nulo: el unico suyo). */
    Pagina<InvitadoResponses.Invitado> listarInvitados(Long residenteId, Long inmuebleId, SolicitudPagina pagina);

    /** Residente.cambiarEstadoInvitado(invitado, id): invierte el estado de un invitado propio. */
    InvitadoResponses.CambioEstado cambiarEstadoInvitado(Long residenteId, Long invitadoId);

    /** Residente.cambiarEstadoInvitados(invitados): invierte el estado de varios invitados propios. */
    InvitadoResponses.CambioEstadoMasivo cambiarEstadoInvitados(Long residenteId, List<Long> invitadoIds);

    /** UsuarioAutorizado.buscarRelacionResidente(): residentes que autorizaron a este invitado. */
    Pagina<InvitadoResponses.Anfitrion> buscarRelacionResidente(Long invitadoId, SolicitudPagina pagina);

    InvitadoResponses.Invitado consultarInvitado(Long residenteId, Long invitadoId);

    InvitadoResponses.Invitado actualizarInvitado(Long residenteId, Long invitadoId,
                                                  InvitadoRequests.ActualizarInvitado solicitud);

    /** Quita la autorizacion del residente; el invitado no se borra (agregacion). */
    void eliminarInvitado(Long residenteId, Long invitadoId);
}
