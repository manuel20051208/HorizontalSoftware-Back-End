package unisimon.demo.model;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import jakarta.persistence.Transient;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Vigilante (hereda de Usuario e implementa NoAdmin).
 * Sus operaciones del diagrama estan aqui con su firma. Trabajan sobre el contexto que deja el servicio en
 * {@code reservas} e {@code invitados}; leer y guardar en la base de datos lo hace la capa de servicios.
 */
@Entity
@DynamicUpdate
@DiscriminatorValue(Usuario.VIGILANTE)
@Getter
@Setter
@NoArgsConstructor
public class Vigilante extends Usuario implements NoAdmin {

    @Column(name = "vigilante_inicio_contrato")
    private LocalDate inicioContrato;

    @Column(name = "vigilante_vence_contrato")
    private LocalDate venceContrato;

    /** Contexto (no es atributo del diagrama ni se guarda): las reservas que ve la porteria. */
    @Transient
    private List<ReservaZonaComun> reservas = new ArrayList<>();

    /** Contexto (no es atributo del diagrama ni se guarda): los invitados registrados. */
    @Transient
    private List<UsuarioAutorizado> invitados = new ArrayList<>();

    public Vigilante(Long id, Long identificacion, String nombreCompleto, LocalDate fechaNacimiento,
                     String correoElectronico, String contrasena, String tipoUsuario,
                     LocalDate inicioContrato, LocalDate venceContrato) {
        super(id, identificacion, nombreCompleto, fechaNacimiento, correoElectronico, contrasena, tipoUsuario);
        this.inicioContrato = inicioContrato;
        this.venceContrato = venceContrato;
    }

    public boolean contratoVigente() {
        LocalDate hoy = LocalDate.now();
        return (inicioContrato == null || !hoy.isBefore(inicioContrato))
                && (venceContrato == null || !hoy.isAfter(venceContrato));
    }

    /**
     * verificarAccesoZonaComun(usuario : Usuario) : Boolean.
     * Entra quien tiene la cuenta activa y, ahora mismo, una reserva EN CURSO de una zona activa: suya o, si es
     * un invitado con la autorizacion vigente, de alguno de los residentes que lo autorizaron.
     */
    public Boolean verificarAccesoZonaComun(Usuario usuario) {
        if (usuario == null || !usuario.isActive()) {
            return false;
        }
        List<Long> duenos = new ArrayList<>();
        duenos.add(usuario.getId());
        if (usuario instanceof UsuarioAutorizado invitado) {
            if (!existeInvitado(invitado.getIdentificacion()) || !invitado.autorizacionVigente()) {
                return false;
            }
            invitado.buscarRelacionResidente().forEach(r -> duenos.add(r.getId()));
        }
        LocalDateTime ahora = LocalDateTime.now();
        return reservas.stream().anyMatch(r -> r.getDuenoReserva() != null
                && duenos.contains(r.getDuenoReserva().getId())
                && r.getZonaComun() != null && Boolean.TRUE.equals(r.getZonaComun().getIsActive())
                && r.intervaloValido() && ReservaZonaComun.EN_CURSO.equals(r.estado(ahora)));
    }

    /** existeInvitado(identificacion : Long) : Boolean */
    public Boolean existeInvitado(Long identificacion) {
        return identificacion != null
                && invitados.stream().anyMatch(i -> identificacion.equals(i.getIdentificacion()));
    }

    /** consultarReservas() : List&lt;ZonaComun&gt; -> las zonas comunes que tienen reserva, sin repetir. */
    public List<ZonaComun> consultarReservas() {
        List<ZonaComun> zonas = new ArrayList<>();
        for (ReservaZonaComun reserva : reservas) {
            ZonaComun zona = reserva.getZonaComun();
            if (zona != null && zonas.stream().noneMatch(z -> z == zona || (z.getId() != null && z.getId().equals(zona.getId())))) {
                zonas.add(zona);
            }
        }
        return zonas;
    }

    /**
     * registrarIncidencia(descripcion : String): solo un vigilante activo y con contrato vigente registra
     * incidencias, y la descripcion es obligatoria. La notificacion INCIDENCIA la arma y envia el servicio.
     */
    public void registrarIncidencia(String descripcion) {
        if (!isActive() || !contratoVigente()) {
            throw new IllegalStateException("El vigilante no esta activo o su contrato no esta vigente");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion de la incidencia es obligatoria");
        }
    }
}
