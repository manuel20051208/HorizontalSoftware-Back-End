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
import unisimon.demo.repository.UsuarioRepository.UsuarioListaView;
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

    private CredencialesView residente(boolean activo, Long inmuebleId) {
        return Proyecciones.de(CredencialesView.class, "id", 5L, "identificacion", 1001L,
                "nombreCompleto", "Ana Perez", "tipoUsuario", "RESIDENTE", "active", activo,
                "token", "huella", "inmuebleId", inmuebleId);
    }

    private InmuebleBasicoView inmueble(Long id, boolean activo) {
        return Proyecciones.de(InmuebleBasicoView.class, "id", id, "direccionInmueble", "B-201",
                "isActive", activo);
    }

    @Test
    @DisplayName("consultar devuelve el inmueble de la cuenta con su direccion")
    void consultar() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(false, 2L)));
        when(inmuebles.buscarBasico(2L)).thenReturn(Optional.of(inmueble(2L, true)));

        UsuarioResponses.RelacionInmueble r = servicio.consultar(1L, 1001L);

        assertThat(r.identificacion()).isEqualTo(1001L);
        assertThat(r.nombreCompleto()).isEqualTo("Ana Perez");
        assertThat(r.inmuebleId()).isEqualTo(2L);
        assertThat(r.direccionInmueble()).isEqualTo("B-201");
        assertThat(r.active()).isFalse();
    }

    @Test
    @DisplayName("consultar devuelve inmueble y direccion nulos cuando la cuenta no tiene inmueble")
    void consultarSinInmueble() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, null)));

        UsuarioResponses.RelacionInmueble r = servicio.consultar(1L, 1001L);

        assertThat(r.inmuebleId()).isNull();
        assertThat(r.direccionInmueble()).isNull();
        verify(inmuebles, never()).buscarBasico(any());
    }

    @Test
    @DisplayName("solo un administrador vigente gestiona la relacion y solo con cuentas de residente")
    void rechazosDeAutorizacion() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorId(2L)).thenReturn(Optional.empty());
        when(usuarios.buscarCredencialesPorId(3L)).thenReturn(Optional.of(
                Proyecciones.de(CredencialesView.class, "id", 3L, "tipoUsuario", "VIGILANTE", "active", true)));
        when(usuarios.buscarCredencialesPorId(4L)).thenReturn(Optional.of(admin(false)));

        assertThatThrownBy(() -> servicio.consultar(2L, 1001L)).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.consultar(3L, 1001L)).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.consultar(4L, 1001L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no esta activo");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(Proyecciones.de(CredencialesView.class, "id", 6L, "identificacion", 1001L,
                        "tipoUsuario", "USUARIO_AUTORIZADO")));
        assertThatThrownBy(() -> servicio.consultar(1L, 1001L)).isInstanceOf(ProhibidoException.class)
                .hasMessageContaining("invitados");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L))
                .thenReturn(Optional.of(Proyecciones.de(CredencialesView.class, "id", 7L, "identificacion", 1001L,
                        "tipoUsuario", "VIGILANTE")));
        assertThatThrownBy(() -> servicio.consultar(1L, 1001L)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Solo los residentes");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar(1L, 1001L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("listarPorInmueble devuelve sus residentes y 404 si el inmueble no existe")
    void listarPorInmueble() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(inmuebles.existePorId(2L)).thenReturn(true);
        when(usuarios.listarResidentesDeInmueble(2L)).thenReturn(List.of(
                Proyecciones.de(UsuarioListaView.class, "id", 5L, "identificacion", 1001L,
                        "nombreCompleto", "Ana Perez", "tipoUsuario", "RESIDENTE", "active", true,
                        "inmuebleId", 2L, "direccionInmueble", "B-201")));

        List<UsuarioResponses.RelacionInmueble> filas = servicio.listarPorInmueble(1L, 2L);

        assertThat(filas).extracting(UsuarioResponses.RelacionInmueble::nombreCompleto)
                .containsExactly("Ana Perez");
        assertThat(filas.get(0).direccionInmueble()).isEqualTo("B-201");

        when(inmuebles.existePorId(9L)).thenReturn(false);
        assertThatThrownBy(() -> servicio.listarPorInmueble(1L, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("vincular asigna el inmueble sin reactivar la cuenta")
    void vincular() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(false, null)));
        when(inmuebles.buscarBasico(2L)).thenReturn(Optional.of(inmueble(2L, true)));

        UsuarioResponses.RelacionInmueble r = servicio.vincular(1L, 1001L, 2L);

        assertThat(r.inmuebleId()).isEqualTo(2L);
        verify(usuarios).actualizarInmueble(5L, 2L);
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
    }

    @Test
    @DisplayName("vincular rechaza una cuenta que ya tiene inmueble y un inmueble inexistente o inactivo")
    void vincularRechazos() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, 2L)));
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("use cambiar");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, null)));
        when(inmuebles.buscarBasico(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, false)));
        assertThatThrownBy(() -> servicio.vincular(1L, 1001L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");

        verify(usuarios, never()).actualizarInmueble(any(), any());
    }

    @Test
    @DisplayName("cambiar mueve los invitados exclusivos, desvincula los compartidos y libera el parqueadero")
    void cambiar() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, 2L)));
        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, true)));
        when(invitados.listarExclusivosDeResidente(5L)).thenReturn(List.of(7L));
        when(invitados.listarIdsDeResidente(5L)).thenReturn(List.of(7L, 8L));

        UsuarioResponses.RelacionInmueble r = servicio.cambiar(1L, 1001L, 3L);

        assertThat(r.inmuebleId()).isEqualTo(3L);
        verify(invitados).moverAInmueble(List.of(7L), 3L);
        verify(invitados).borrarVinculo(5L, 8L);
        verify(invitados, never()).borrarVinculo(5L, 7L);
        verify(autos).liberarParqueaderoDe(5L);
        verify(reservas).cancelarProgramadasFuturas(5L, 3L, AHORA);
        verify(usuarios).actualizarInmueble(5L, 3L);
    }

    @Test
    @DisplayName("cambiar exige que la cuenta ya tenga un inmueble distinto y valida el inmueble nuevo")
    void cambiarRechazos() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, null)));
        assertThatThrownBy(() -> servicio.cambiar(1L, 1001L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("use vincular");

        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, 2L)));
        assertThatThrownBy(() -> servicio.cambiar(1L, 1001L, 2L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya esta relacionada");

        when(inmuebles.buscarBasico(3L)).thenReturn(Optional.of(inmueble(3L, false)));
        assertThatThrownBy(() -> servicio.cambiar(1L, 1001L, 3L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");

        verify(usuarios, never()).actualizarInmueble(any(), any());
        verifyNoInteractions(autos, reservas);
    }

    @Test
    @DisplayName("desvincular desactiva la cuenta activa, sus invitados exclusivos y cancela sus reservas")
    void desvincular() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, 2L)));
        when(invitados.listarExclusivosDeResidente(5L)).thenReturn(List.of(7L));

        servicio.desvincular(1L, 1001L);

        verify(invitados).desactivar(List.of(7L));
        verify(invitados).borrarVinculosDeResidente(5L);
        verify(autos).liberarParqueaderoDe(5L);
        verify(reservas).cancelarProgramadasFuturas(5L, null, AHORA);
        verify(usuarios).actualizarActive(5L, false);
        verify(usuarios).actualizarToken(5L, null);
        verify(usuarios).actualizarInmueble(5L, null);
    }

    @Test
    @DisplayName("desvincular una cuenta inactiva no vuelve a tocar su estado, pero limpia la relacion")
    void desvincularInactiva() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(false, 2L)));
        when(invitados.listarExclusivosDeResidente(5L)).thenReturn(List.of());

        servicio.desvincular(1L, 1001L);

        verify(invitados, never()).desactivar(any());
        verify(usuarios, never()).actualizarActive(any(), anyBoolean());
        verify(usuarios, never()).actualizarToken(any(), any());
        verify(usuarios).actualizarInmueble(5L, null);
    }

    @Test
    @DisplayName("desvincular exige que la cuenta tenga un inmueble")
    void desvincularSinInmueble() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(admin(true)));
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(residente(true, null)));

        assertThatThrownBy(() -> servicio.desvincular(1L, 1001L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("no tiene un inmueble");
        verify(usuarios, never()).actualizarInmueble(any(), any());
    }
}
