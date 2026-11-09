package unisimon.demo.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unisimon.demo.dto.response.InvitadoResponses;
import unisimon.demo.dto.response.ReservaResponses;
import unisimon.demo.dto.response.VigilanteResponses;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.mapper.Hidratador;
import unisimon.demo.mapper.InvitadoMapper;
import unisimon.demo.mapper.ReservaMapper;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.model.ZonaComun;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.InvitadoRepository.AnfitrionView;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.ReservaZonaComunRepository.AccesoView;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.VigilanteService;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/** Porteria: consulta de reservas, verificacion de acceso a zonas e ingreso de invitados. */
@Service
public class VigilanteServiceImpl implements VigilanteService {

    private final ReservaZonaComunRepository reservas;
    private final InvitadoRepository invitados;
    private final UsuarioRepository usuarios;
    private final NotificacionService notificaciones;
    private final Clock reloj;

    public VigilanteServiceImpl(ReservaZonaComunRepository reservas, InvitadoRepository invitados,
                                UsuarioRepository usuarios, NotificacionService notificaciones, Clock reloj) {
        this.reservas = reservas;
        this.invitados = invitados;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponses.Reserva> consultarReservas() {
        LocalTime ahora = LocalTime.now(reloj);
        return reservas.listarTodas().stream().map(r -> {
            ZonaComun zona = Hidratador.referenciaZona(r.getZonaComunId());
            zona.setHoraInicio(r.getHoraInicio());
            zona.setHoraFin(r.getHoraFin());
            return ReservaMapper.de(r, Boolean.TRUE.equals(r.getZonaActiva()) && zona.horarioIncluye(ahora));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VigilanteResponses.Acceso verificarAccesoZonaComun(Long identificacion) {
        CredencialesView persona = usuarios.buscarCredencialesPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una persona con esa identificacion"));
        if (!Boolean.TRUE.equals(persona.getActive())) {
            return denegar(persona, "La cuenta de la persona esta inactiva");
        }
        // Un invitado solo entra mientras su autorizacion no haya vencido.
        InvitadoView invitado = invitados.buscarPorIdentificacion(identificacion).orElse(null);
        if (invitado != null && !Hidratador.invitado(invitado).autorizacionVigente()) {
            return denegar(persona, "La autorizacion del invitado vencio el " + invitado.getVenceAutorizacion());
        }

        LocalTime ahora = LocalTime.now(reloj);
        List<VigilanteResponses.ZonaPermitida> permitidas = reservas.listarZonasConAcceso(identificacion).stream()
                .filter(z -> abiertaAhora(z, ahora))
                .map(z -> new VigilanteResponses.ZonaPermitida(z.getZonaComunId(), z.getNombreZonaComun(),
                        z.getHoraInicio(), z.getHoraFin()))
                .toList();
        if (permitidas.isEmpty()) {
            return denegar(persona, "No tiene una reserva de una zona abierta en este momento");
        }
        return new VigilanteResponses.Acceso(identificacion, persona.getNombreCompleto(), true,
                "Acceso permitido", permitidas);
    }

    @Override
    @Transactional(readOnly = true)
    public VigilanteResponses.InvitadoConsulta existeInvitado(Long identificacion) {
        return invitados.buscarPorIdentificacion(identificacion)
                .map(v -> {
                    InvitadoResponses.Invitado respuesta = InvitadoMapper.invitado(v);
                    return new VigilanteResponses.InvitadoConsulta(true, respuesta.vigente(), respuesta);
                })
                .orElseGet(() -> new VigilanteResponses.InvitadoConsulta(false, false, null));
    }

    @Override
    @Transactional
    public VigilanteResponses.Ingreso registrarIngreso(Long identificacion) {
        InvitadoView vista = invitados.buscarPorIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay un invitado con esa identificacion"));
        UsuarioAutorizado invitado = Hidratador.invitado(vista);
        if (!invitado.isActive()) {
            return new VigilanteResponses.Ingreso(false, "El invitado no esta activo", vista.getNombreCompleto(), List.of());
        }
        if (!invitado.autorizacionVigente()) {
            return new VigilanteResponses.Ingreso(false, "La autorizacion vencio el " + vista.getVenceAutorizacion(),
                    vista.getNombreCompleto(), List.of());
        }
        List<AnfitrionView> anfitriones = invitados.listarAnfitriones(vista.getId());
        List<String> avisados = anfitriones.stream().map(AnfitrionView::getInmuebleId).filter(Objects::nonNull)
                .distinct()
                .map(inmuebleId -> {
                    notificaciones.notificarVisita(inmuebleId, vista.getNombreCompleto());   // Notificacion.notificarVisita
                    return anfitriones.stream().filter(a -> inmuebleId.equals(a.getInmuebleId()))
                            .findFirst().map(AnfitrionView::getDireccionInmueble).orElse(String.valueOf(inmuebleId));
                })
                .toList();
        return new VigilanteResponses.Ingreso(true, "Ingreso permitido", vista.getNombreCompleto(), avisados);
    }

    private static boolean abiertaAhora(AccesoView zona, LocalTime ahora) {
        ZonaComun dominio = Hidratador.referenciaZona(zona.getZonaComunId());
        dominio.setHoraInicio(zona.getHoraInicio());
        dominio.setHoraFin(zona.getHoraFin());
        return Boolean.TRUE.equals(zona.getZonaActiva()) && dominio.horarioIncluye(ahora);
    }

    private static VigilanteResponses.Acceso denegar(CredencialesView persona, String motivo) {
        return new VigilanteResponses.Acceso(persona.getIdentificacion(), persona.getNombreCompleto(), false, motivo,
                List.of());
    }
}
