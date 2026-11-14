package unisimon.demo.mapper;

import unisimon.demo.model.Administrador;
import unisimon.demo.model.Inmueble;
import unisimon.demo.model.Liquidacion;
import unisimon.demo.model.Pago;
import unisimon.demo.model.PasarelaPago;
import unisimon.demo.model.ReglaReserva;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Rompimiento;
import unisimon.demo.model.Usuario;
import unisimon.demo.model.UsuarioAutorizado;
import unisimon.demo.model.Vigilante;
import unisimon.demo.model.ZonaComun;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InvitadoRepository.InvitadoView;
import unisimon.demo.repository.LiquidacionRepository.LiquidacionView;
import unisimon.demo.repository.PagoRepository.PagoView;
import unisimon.demo.repository.PasarelaPagoRepository.PasarelaView;
import unisimon.demo.repository.ReglaReservaRepository.ReglaView;
import unisimon.demo.repository.ResidenteRepository.ContextoResidenteView;
import unisimon.demo.repository.RompimientoRepository.RompimientoView;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.PreferenciasView;
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
import unisimon.demo.repository.ZonaComunRepository.ZonaView;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Arma objetos del modelo (desconectados de cualquier base de datos) a partir de lo que devuelven las
 * consultas nativas. Asi las reglas del diagrama (calcularTotal, estaVencida, validarHorario...) se ejecutan
 * en las clases del dominio y no se repiten en SQL ni en los servicios.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Hidratador {

    // ------------------------------------------------------------------ referencias (solo el id)

    public static Inmueble referenciaInmueble(Long id) {
        Inmueble inmueble = new Inmueble();
        inmueble.setId(id);
        return inmueble;
    }

    public static Usuario referenciaUsuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    /**
     * Usuario de su tipo (Administrador, Residente, Vigilante o UsuarioAutorizado) con sus credenciales, para
     * ejecutar las operaciones de cuenta del diagrama. Un residente lleva la referencia de su inmueble.
     */
    public static Usuario usuario(CredencialesView v) {
        String tipo = v.getTipoUsuario() == null ? "" : v.getTipoUsuario();
        Usuario usuario = switch (tipo) {
            case Usuario.ADMINISTRADOR -> new Administrador();
            case Usuario.VIGILANTE -> new Vigilante();
            case Usuario.USUARIO_AUTORIZADO -> new UsuarioAutorizado();
            case Usuario.RESIDENTE -> new Residente();
            default -> new Usuario();
        };
        usuario.setId(v.getId());
        usuario.setIdentificacion(v.getIdentificacion());
        usuario.setNombreCompleto(v.getNombreCompleto());
        usuario.setCorreoElectronico(v.getCorreoElectronico());
        usuario.setContrasena(v.getContrasena());
        usuario.setTipoUsuario(v.getTipoUsuario());
        usuario.setToken(v.getToken());
        usuario.setActive(Boolean.TRUE.equals(v.getActive()));
        usuario.setNotificacionLeida(Boolean.TRUE.equals(v.getNotificacionLeida()));
        if (usuario instanceof Residente residente && v.getInmuebleId() != null) {
            residente.setInmueble(referenciaInmueble(v.getInmuebleId()));
        }
        return usuario;
    }

    /** Usuario con lo que necesita configurarPreferencias: si esta activo y sus preferencias actuales. */
    public static Usuario usuarioConPreferencias(Long id, PreferenciasView v) {
        Usuario usuario = referenciaUsuario(id);
        usuario.setActive(Boolean.TRUE.equals(v.getActive()));
        String preferencias = v.getPreferencias();
        usuario.setPreferenciasNotificacion(preferencias == null || preferencias.isBlank()
                ? new ArrayList<>() : new ArrayList<>(Arrays.asList(preferencias.split(","))));
        return usuario;
    }

    public static Residente referenciaResidente(Long id) {
        Residente residente = new Residente();
        residente.setId(id);
        return residente;
    }

    public static Administrador referenciaAdministrador(Long id) {
        Administrador admin = new Administrador();
        admin.setId(id);
        return admin;
    }

    public static ZonaComun referenciaZona(Long id) {
        ZonaComun zona = new ZonaComun();
        zona.setId(id);
        return zona;
    }

    public static ReglaReserva referenciaRegla(Long id) {
        ReglaReserva regla = new ReglaReserva();
        regla.setId(id);
        return regla;
    }

    // ------------------------------------------------------------------ objetos completos

    public static Liquidacion liquidacion(LiquidacionView v) {
        Liquidacion l = new Liquidacion(v.getId(), referenciaInmueble(v.getInmuebleId()), v.getPeriodo(),
                v.getFechaLimite(), v.getCuotaAdministracion(), v.getValorServiciosComunes(), v.getValorMultas());
        l.setFechaGeneracion(v.getFechaGeneracion());
        l.setTotal(v.getTotal());
        l.setEstado(v.getEstado());
        return l;
    }

    /** Pago con su inmueble (id y direccion) y, si se conoce, la liquidacion vigente de ese inmueble. */
    public static Pago pago(PagoView v, Optional<LiquidacionView> liquidacionVigente) {
        Inmueble inmueble = referenciaInmueble(v.getInmuebleId());
        inmueble.setDireccionInmueble(v.getDireccionInmueble());
        liquidacionVigente.map(Hidratador::liquidacion).ifPresent(inmueble::setLiquidacion);
        Pago pago = new Pago(v.getId(), inmueble, v.getMonto(), v.getFechaPago(), v.getConcepto(),
                v.getMetodoPago(), v.getReferencia());
        pago.setEstado(v.getEstado());
        return pago;
    }

    public static Pago pago(PagoView v) {
        return pago(v, Optional.empty());
    }

    public static ZonaComun zona(ZonaView v) {
        ZonaComun zona = new ZonaComun(v.getId(), v.getNombreZonaComun(), v.getCapacidad(), v.getHoraInicio(),
                v.getHoraFin());
        zona.setIsActive(v.getIsActive());
        return zona;
    }

    public static ReglaReserva regla(ReglaView v) {
        return new ReglaReserva(v.getId(), v.getAnticipacionMinimaHoras(), v.getDuracionMaximaHoras(),
                v.getHorasAntelacionPenalidad(), v.getTarifaPenalidad(), referenciaAdministrador(v.getAdminId()));
    }

    public static Rompimiento rompimiento(RompimientoView v) {
        Rompimiento r = new Rompimiento(v.getId(), referenciaZona(v.getZonaComunId()),
                referenciaRegla(v.getReglaReservaId()), v.getFechaAplicacion(), v.getObservaciones());
        r.setActiva(v.getActiva());
        return r;
    }

    public static PasarelaPago pasarela(PasarelaView v) {
        PasarelaPago p = new PasarelaPago(v.getId(), v.getNombrePasarela(), v.getUrlApi(), v.getApiKey());
        p.setEstado(v.getEstado());
        return p;
    }

    /** Residente con su inmueble y la liquidacion vigente: lo que necesita estaHabilitado(...). */
    public static Residente residente(ContextoResidenteView v) {
        Inmueble inmueble = referenciaInmueble(v.getInmuebleId());
        inmueble.setDireccionInmueble(v.getDireccionInmueble());
        inmueble.setIsActive(v.getInmuebleActive());
        if (v.getLiquidacionId() != null) {
            Liquidacion l = new Liquidacion();
            l.setId(v.getLiquidacionId());
            l.setEstado(v.getLiquidacionEstado());
            l.setFechaLimite(v.getLiquidacionFechaLimite());
            l.setTotal(v.getLiquidacionTotal());
            l.setInmueble(inmueble);
            inmueble.setLiquidacion(l);
        }
        Residente residente = new Residente();
        residente.setId(v.getId());
        residente.setNombreCompleto(v.getNombreCompleto());
        residente.setActive(v.getActive());
        residente.setInmueble(inmueble);
        return residente;
    }

    public static UsuarioAutorizado invitado(InvitadoView v) {
        UsuarioAutorizado ua = new UsuarioAutorizado();
        ua.setId(v.getId());
        ua.setIdentificacion(v.getIdentificacion());
        ua.setNombreCompleto(v.getNombreCompleto());
        ua.setActive(v.getActive());
        ua.setVenceAutorizacion(v.getVenceAutorizacion());
        return ua;
    }

    /**
     * Inmueble con sus residentes (id, nombre, tipo y estado) y su liquidacion vigente: lo que necesitan los
     * metodos notificarX de Notificacion.
     */
    public static Inmueble inmueble(InmuebleBasicoView base, List<UsuarioListaView> usuarios,
                                    Optional<LiquidacionView> liquidacionVigente) {
        Inmueble inmueble = referenciaInmueble(base.getId());
        inmueble.setDireccionInmueble(base.getDireccionInmueble());
        inmueble.setIsActive(base.getIsActive());
        for (UsuarioListaView u : usuarios) {
            Residente r = Usuario.USUARIO_AUTORIZADO.equals(u.getTipoUsuario())
                    ? new UsuarioAutorizado() : new Residente();
            r.setId(u.getId());
            r.setNombreCompleto(u.getNombreCompleto());
            r.setActive(u.getActive());
            r.setInmueble(inmueble);
            inmueble.getUsuariosRelacionado().add(r);
        }
        liquidacionVigente.map(Hidratador::liquidacion).ifPresent(inmueble::setLiquidacion);
        return inmueble;
    }
}
