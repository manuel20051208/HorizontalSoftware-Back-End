package unisimon.demo.mapper;

import unisimon.demo.dto.response.AutoResponses;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.repository.AutoRepository.AutoView;
import unisimon.demo.repository.AutoRepository.DuenoView;
import unisimon.demo.repository.InvitadoRepository.AnfitrionView;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

/** Proyecciones de invitados y autos -> respuestas del API. "vigente" se pregunta al dominio. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class InvitadoMapper {

    public static InvitadoResponses.Invitado invitado(InvitadoView v) {
        UsuarioAutorizado dominio = Hidratador.invitado(v);
        boolean vigente = dominio.isActive() && dominio.autorizacionVigente();
        return new InvitadoResponses.Invitado(v.getId(), v.getIdentificacion(), v.getNombreCompleto(),
                v.getCorreoElectronico(), v.getActive(), v.getVenceAutorizacion(), vigente,
                UsuarioMapper.inmuebles(v.getInmuebles()), v.getFechaNacimiento());
    }

    public static InvitadoResponses.Anfitrion anfitrion(AnfitrionView v) {
        return new InvitadoResponses.Anfitrion(v.getId(), v.getIdentificacion(), v.getNombreCompleto(),
                UsuarioMapper.inmuebles(v.getInmuebles()));
    }

    public static AutoResponses.Auto auto(AutoView v) {
        return new AutoResponses.Auto(v.getId(), v.getDuenoId(), v.getNombreDueno(), v.getPlaca(),
                v.getParqueaderoId(), v.getInmuebleId(), v.getDireccionInmueble());
    }

    public static AutoResponses.Dueno dueno(DuenoView v) {
        return new AutoResponses.Dueno(v.getId(), v.getIdentificacion(), v.getNombreCompleto(), v.getInmuebleId(),
                v.getDireccionInmueble());
    }
}
