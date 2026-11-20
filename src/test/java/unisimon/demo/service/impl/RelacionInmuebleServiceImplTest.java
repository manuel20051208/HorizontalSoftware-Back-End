package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.repository.AutoRepository;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.InmuebleRepository.InmuebleBasicoView;
import unisimon.demo.repository.InvitadoRepository;
import unisimon.demo.repository.ReservaZonaComunRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.repository.UsuarioRepository.UsuarioDeInmuebleView;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelacionInmuebleServiceImplTest {

    /** El reloj fijo marca el 2026-11-08 a las 15:00. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 15, 0);

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private InvitadoRepository invitados;
    @Mock
    private AutoRepository autos;
    @Mock
    private ReservaZonaComunRepository reservas;

    private RelacionInmuebleServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        servicio = new RelacionInmuebleServiceImpl(usuarios, inmuebles, invitados, autos, reservas, reloj);
    }

    private CredencialesView admin(boolean activo) {
        return Proyecciones.de(CredencialesView.class, "id", 1L, "identificacion", 900L,
                "nombreCompleto", "Admin Torres", "tipoUsuario", "ADMINISTRADOR", "active", activo);
    }

    /** El texto de inmuebles es el string_agg de la base: "id|direccion;id|direccion". */
    private CredencialesView cuenta(Long id, String tipo, boolean activo, String inmueblesTexto) {
        return Proyecciones.de(CredencialesView.class, "id", id, "identificacion", 1001L,
                "nombreCompleto", "Ana Perez", "tipoUsuario", tipo, "active", activo,
                "token", "huella", "inmuebles", inmueblesTexto);
    }

    private CredencialesView residente(boolean activo, String inmueblesTexto) {
        return cuenta(5L, "RESIDENTE", activo, inmueblesTexto);
    }

    private CredencialesView invitado(Long id, boolean activo) {
        return Proyecciones.de(CredencialesView.class, "id", id, "identificacion", 2000L + id,
                "nombreCompleto", "Invitado " + id, "tipoUsuario", "USUARIO_AUTORIZADO", "active", activo,
                "token", "huella", "inmuebles", "2|B-201");
    }

    private InmuebleBasicoView inmueble(Long id, boolean activo, String direccion) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", id, "direccionInmueble", direccion,
                "isActive", activo);
    }

    private void administradorVigente() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
    }

    // ------------------------------------------------------------------ consultar

    @Test
    @DisplayName("consultar devuelve TODOS los inmuebles de la cuenta, no solo uno")
    void consultar() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(false, "2|B-201;3|C-101")));
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(2L, true, "B-201"), inmueble(3L, true, "C-101")));

        UsuarioResponses.UsuarioInmuebles r = servicio.consultar(1L, 1001L);

        assertThat(r.identificacion()).isEqualTo(1001L);
        assertThat(r.nombreCompleto()).isEqualTo("Ana Perez");
        assertThat(r.active()).isFalse();
        assertThat(r.inmuebles()).extracting(UsuarioResponses.InmuebleResumen::id).containsExactly(2L, 3L);
        assertThat(r.inmuebles()).extracting(UsuarioResponses.InmuebleResumen::direccionInmueble)
                .containsExactly("B-201", "C-101");
    }

    @Test
    @DisplayName("consultar devuelve la lista vacia si la cuenta no tiene inmueble")
    void consultarSinInmueble() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, null)));
        when(usuarios.listarInmueblesDe(5L)).thenReturn(List.of());

        UsuarioResponses.UsuarioInmuebles r = servicio.consultar(1L, 1001L);

        assertThat(r.inmuebles()).isEmpty();
    }

    @Test
    @DisplayName("solo un administrador vigente gestiona la relacion; invitados si, vigilantes no")
    void rechazosDeAutorizacion() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorId(2L)).thenReturn(Optional.empty());
        when(usuarios.buscarCredencialesPorId(3L)).thenReturn(Optional.of(
                Proyecciones.de(CredencialesView.class, "id", 3L, "tipoUsuario", "VIGILANTE", "active", true)));
        when(usuarios.buscarCredencialesPorId(4L)).thenReturn(Optional.of(admin(false)));

        assertThatThrownBy(() -> servicio.consultar(2L, 1001L)).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.consultar(3L, 1001L)).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.consultar(4L, 1001L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no esta activo");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(cuenta(6L, "USUARIO_AUTORIZADO", true, "2|B-201")));
        when(usuarios.listarInmueblesDe(6L)).thenReturn(List.of(inmueble(2L, true, "B-201")));
        assertThat(servicio.consultar(1L, 1001L).tipoUsuario()).isEqualTo("USUARIO_AUTORIZADO");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(cuenta(7L, "VIGILANTE", true, null)));
        assertThatThrownBy(() -> servicio.consultar(1L, 1001L)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Solo residentes e invitados");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar(1L, 1001L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ listar por inmueble

    @Test
    @DisplayName("listarUsuariosDeInmueble devuelve residentes e invitados; 404 si el inmueble no existe")
    void listarUsuariosDeInmueble() {
        administradorVigente();
        when(inmuebles.existePorId(2L)).thenReturn(true);
        when(usuarios.listarUsuariosDeInmueble(2L)).thenReturn(List.of(
                Proyecciones.de(UsuarioDeInmuebleView.class, "id", 5L, "identificacion", 1001L,
                        "nombreCompleto", "Ana Perez", "tipoUsuario", "RESIDENTE", "active", true),
                Proyecciones.de(UsuarioDeInmuebleView.class, "id", 9L, "identificacion", 2002L,
                        "nombreCompleto", "Mariana", "tipoUsuario", "USUARIO_AUTORIZADO", "active", false)));

        List<UsuarioResponses.UsuarioDeInmueble> filas = servicio.listarUsuariosDeInmueble(1L, 2L);

        assertThat(filas).extracting(UsuarioResponses.UsuarioDeInmueble::tipoUsuario)
                .containsExactly("RESIDENTE", "USUARIO_AUTORIZADO");
        assertThat(filas.get(1).active()).isFalse();

        when(inmuebles.existePorId(9L)).thenReturn(false);
        assertThatThrownBy(() -> servicio.listarUsuariosDeInmueble(1L, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ vincular

    @Test
    @DisplayName("vincular agrega un segundo inmueble sin reactivar ni verificar el correo")
    void vincular() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(false, "2|B-201")));
        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, true, "C-101")));
        when(usuarios.estaRelacionado(5L, 3L)).thenReturn(false);
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(2L, true, "B-201"), inmueble(3L, true, "C-101")));

        UsuarioResponses.UsuarioInmuebles r = servicio.vincular(1L, 1001L, 3L);

        assertThat(r.inmuebles()).hasSize(2);
        verify(usuarios).vincularInmueble(5L, 3L);
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
        verify(usuarios, never()).marcarCorreoVerificado(any());
    }

    @Test
    @DisplayName("vincular rechaza un vinculo repetido y un inmueble inexistente o inactivo")
    void vincularRechazos() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201")));
        when(inmuebles.buscarBasico(2L)).thenReturn(Optional.of(inmueble(2L, true, "B-201")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 2L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya esta relacionada");

        when(inmuebles.buscarBasico(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, false, "C-101")));
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");

        verify(usuarios, never()).vincularInmueble(any(), any());
    }

    // ------------------------------------------------------------------ mudar

    @Test
    @DisplayName("mudar mueve invitados exclusivos, deja los compartidos, libera parqueadero y mueve reservas")
    void mudar() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(usuarios.estaRelacionado(5L, 3L)).thenReturn(false);
        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, true, "C-101")));
        when(invitados.listarIdsDeResidenteEnInmueble(5L, 2L)).thenReturn(List.of(7L, 8L));
        when(invitados.tieneOtroAnfitrionEnInmueble(7L, 5L, 2L)).thenReturn(false);
        when(invitados.tieneOtroAnfitrionEnInmueble(8L, 5L, 2L)).thenReturn(true);
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(2L, true, "B-201"), inmueble(3L, true, "C-101")));

        UsuarioResponses.UsuarioInmuebles r = servicio.mudar(1L, 1001L, 2L, 3L);

        assertThat(r.inmuebles()).hasSize(2);
        verify(invitados).vincularAInmueble(List.of(7L), 3L);
        verify(invitados).vincularAInmueble(List.of(8L), 3L);
        verify(invitados).desvincularDeInmueble(List.of(7L), 2L);
        verify(invitados, never()).desvincularDeInmueble(List.of(8L), 2L);
        verify(autos).liberarParqueaderoDeInmueble(5L, 2L);
        verify(reservas).moverFuturasAInmueble(5L, 2L, 3L, AHORA);
        verify(reservas).cancelarFuturasDeInmueble(5L, 2L, AHORA);
        verify(usuarios).desvincularInmueble(5L, 2L);
        verify(usuarios).vincularInmueble(5L, 3L);
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
        verify(usuarios, never()).actualizarToken(any(), any());
    }

    @Test
    @DisplayName("mudar solo toca invitados del origen X: el que estaba solo en Z no pierde X ni gana Y")
    void mudarSoloAfectaInvitadosDelOrigen() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201;4|D-101")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(usuarios.estaRelacionado(5L, 3L)).thenReturn(false);
        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, true, "C-101")));
        // La consulta de la puente ya devuelve solo los invitados relacionados con X (2); el invitado 9 solo esta en Z (4).
        when(invitados.listarIdsDeResidenteEnInmueble(5L, 2L)).thenReturn(List.of(7L, 8L));
        when(invitados.tieneOtroAnfitrionEnInmueble(7L, 5L, 2L)).thenReturn(false);
        when(invitados.tieneOtroAnfitrionEnInmueble(8L, 5L, 2L)).thenReturn(true);
        when(usuarios.listarInmueblesDe(5L))
                .thenReturn(List.of(inmueble(3L, true, "C-101"), inmueble(4L, true, "D-101")));

        servicio.mudar(1L, 1001L, 2L, 3L);

        // El invitado 7, sin otro anfitrion en X, pierde X y gana Y.
        verify(invitados).desvincularDeInmueble(List.of(7L), 2L);
        verify(invitados).vincularAInmueble(List.of(7L), 3L);
        // El invitado 8, con otro anfitrion en X, conserva X y tambien gana Y.
        verify(invitados, never()).desvincularDeInmueble(List.of(8L), 2L);
        verify(invitados).vincularAInmueble(List.of(8L), 3L);
        // El invitado 9, ligado solo a Z, no se menciona en ninguna operacion.
        verify(invitados, never()).vincularAInmueble(List.of(9L), 3L);
        verify(invitados, never()).desvincularDeInmueble(List.of(9L), 2L);
        verify(invitados, never()).tieneOtroAnfitrionEnInmueble(9L, 5L, 2L);
        verify(usuarios, never()).buscarCredencialesPorId(9L);
    }

    @Test
    @DisplayName("mudar exige origen relacionado, destino libre y destino activo")
    void mudarRechazos() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201")));

        when(usuarios.estaRelacionado(5L, 9L)).thenReturn(false);
        assertThatThrownBy(() -> servicio.mudar(1L, 1001L, 9L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("origen");

        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(usuarios.estaRelacionado(5L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> servicio.mudar(1L, 1001L, 2L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("destino");

        when(usuarios.estaRelacionado(5L, 3L)).thenReturn(false);
        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, false, "C-101")));
        assertThatThrownBy(() -> servicio.mudar(1L, 1001L, 2L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");

        verifyNoInteractions(invitados, autos, reservas);
        verify(usuarios, never()).desvincularInmueble(any(), any());
    }

    // ------------------------------------------------------------------ desvincular

    @Test
    @DisplayName("desvincular el ultimo inmueble desactiva la cuenta y sus invitados exclusivos sin reactivarlos")
    void desvincular() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(invitados.listarIdsDeResidenteEnInmueble(5L, 2L)).thenReturn(List.of(7L, 8L));
        when(invitados.tieneOtroAnfitrionEnInmueble(7L, 5L, 2L)).thenReturn(false);
        when(invitados.tieneOtroAnfitrionEnInmueble(8L, 5L, 2L)).thenReturn(true);
        when(usuarios.buscarCredencialesPorId(7L)).thenReturn(Optional.of(invitado(7L, true)));
        when(usuarios.contarInmueblesDe(5L)).thenReturn(0L);
        when(usuarios.contarInmueblesDe(7L)).thenReturn(0L);

        servicio.desvincular(1L, 1001L, 2L);

        verify(invitados).desvincularDeInmueble(List.of(7L), 2L);
        verify(invitados, never()).desvincularDeInmueble(List.of(8L), 2L);
        verify(autos).liberarParqueaderoDeInmueble(5L, 2L);
        verify(reservas).cancelarFuturasDeInmueble(5L, 2L, AHORA);
        verify(reservas, never()).moverFuturasAInmueble(any(), any(), any(), any());
        verify(usuarios).desvincularInmueble(5L, 2L);
        verify(usuarios).actualizarActive(5L, false);
        verify(usuarios).actualizarToken(5L, null);
        verify(usuarios).actualizarActive(7L, false);
        verify(usuarios).actualizarToken(7L, null);
        verify(usuarios, never()).marcarCorreoVerificado(any());
    }

    @Test
    @DisplayName("desvincular conservando otro inmueble mantiene activa la cuenta y su sesion")
    void desvincularConservandoOtroInmueble() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201;3|C-101")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(invitados.listarIdsDeResidenteEnInmueble(5L, 2L)).thenReturn(List.of(7L));
        when(invitados.tieneOtroAnfitrionEnInmueble(7L, 5L, 2L)).thenReturn(true);
        when(usuarios.contarInmueblesDe(5L)).thenReturn(1L);

        servicio.desvincular(1L, 1001L, 2L);

        verify(usuarios).desvincularInmueble(5L, 2L);
        verify(invitados, never()).desvincularDeInmueble(any(), any());
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
        verify(usuarios, never()).actualizarToken(any(), any());
    }

    @Test
    @DisplayName("desvincular una cuenta inactiva limpia el vinculo sin volver a activarla")
    void desvincularInactiva() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(false, "2|B-201")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(true);
        when(invitados.listarIdsDeResidenteEnInmueble(5L, 2L)).thenReturn(List.of());

        servicio.desvincular(1L, 1001L, 2L);

        verify(usuarios).desvincularInmueble(5L, 2L);
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
        verify(usuarios, never()).actualizarToken(any(), any());
    }

    @Test
    @DisplayName("desvincular a un invitado solo quita su vinculo, sin reglas de residente")
    void desvincularInvitado() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(invitado(6L, true)));
        when(usuarios.estaRelacionado(6L, 2L)).thenReturn(true);
        when(usuarios.contarInmueblesDe(6L)).thenReturn(0L);

        servicio.desvincular(1L, 1001L, 2L);

        verify(usuarios).desvincularInmueble(6L, 2L);
        verify(usuarios).actualizarActive(6L, false);
        verifyNoInteractions(invitados, autos, reservas);
    }

    @Test
    @DisplayName("desvincular exige que la cuenta estuviera relacionada con ese inmueble")
    void desvincularNoRelacionado() {
        administradorVigente();
        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(residente(true, "2|B-201")));
        when(usuarios.estaRelacionado(5L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.desvincular(1L, 1001L, 2L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("no estaba relacionada");
        verify(usuarios, never()).desvincularInmueble(any(), any());
    }
}
