package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.dto.request.UsuarioRequests.CrearUsuario;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.ConflictoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.RecursoNoEncontradoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.factory.UsuarioFactory;
import unisimon.demo.model.Residente;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.InmuebleRepository;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.service.support.UsuarioPersistidor;
import unisimon.demo.support.Proyecciones;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioGestionServiceImplTest {

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private InmuebleRepository inmuebles;
    @Mock
    private UsuarioPersistidor persistidor;
    @Mock
    private PasswordEncoder codificador;

    private UsuarioGestionServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new UsuarioGestionServiceImpl(usuarios, inmuebles, new UsuarioFactory(), persistidor, codificador,
                new PoliticaContrasena());
    }

    private CrearUsuario residente(String clave) {
        return new CrearUsuario("residente", 1001L, " Santiago Mendez ", LocalDate.of(1990, 5, 10),
                "Santiago@Mail.com", clave, 5L, null, null);
    }

    private CredencialesView vista(Long id, String tipo, boolean activo) {
        return Proyecciones.de(CredencialesView.class, "id", id, "identificacion", 1001L,
                "nombreCompleto", "Santiago Mendez", "correoElectronico", "santiago@mail.com", "tipoUsuario", tipo,
                "active", activo, "notificacionLeida", false, "fechaCreacionCuenta", LocalDate.now());
    }

    @Test
    @DisplayName("gestionarCuentaUsuario crea un Residente inactivo con la clave codificada")
    void crearResidente() {
        when(usuarios.existePorCorreo("Santiago@Mail.com")).thenReturn(false);
        when(usuarios.existePorIdentificacion(1001L)).thenReturn(false);
        when(inmuebles.existePorId(5L)).thenReturn(true);
        when(codificador.encode("Clave12345")).thenReturn("hash");
        when(persistidor.insertar(any(Usuario.class))).thenReturn(9L);
        when(usuarios.buscarCredencialesPorId(9L)).thenReturn(Optional.of(vista(9L, "RESIDENTE", false)));

        UsuarioResponses.Usuario respuesta = servicio.gestionarCuentaUsuario(residente("Clave12345"));

        assertThat(respuesta.id()).isEqualTo(9L);
        assertThat(respuesta.active()).isFalse();
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(persistidor).insertar(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Residente.class);
        Residente creado = (Residente) captor.getValue();
        assertThat(creado.getTipoUsuario()).isEqualTo("RESIDENTE");
        assertThat(creado.getNombreCompleto()).isEqualTo("Santiago Mendez");
        assertThat(creado.getCorreoElectronico()).isEqualTo("santiago@mail.com");
        assertThat(creado.getContrasena()).isEqualTo("hash");
        assertThat(creado.isActive()).isFalse();
        assertThat(creado.getInmueble().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("no permite correo o identificacion repetidos")
    void duplicados() {
        when(usuarios.existePorCorreo("Santiago@Mail.com")).thenReturn(true);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(residente("Clave12345")))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("correo");

        when(usuarios.existePorCorreo("Santiago@Mail.com")).thenReturn(false);
        when(usuarios.existePorIdentificacion(1001L)).thenReturn(true);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(residente("Clave12345")))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("identificacion");
        verify(persistidor, never()).insertar(any());
    }

    @Test
    @DisplayName("rechaza una contrasena debil y un inmueble inexistente")
    void validaciones() {
        when(usuarios.existePorCorreo(any())).thenReturn(false);
        when(usuarios.existePorIdentificacion(any())).thenReturn(false);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(residente("abc")))
                .isInstanceOf(SolicitudInvalidaException.class);

        when(inmuebles.existePorId(5L)).thenReturn(false);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(residente("Clave12345")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(persistidor, never()).insertar(any());
    }

    @Test
    @DisplayName("un residente sin inmueble o un tipo desconocido se rechazan")
    void tipoYDatosObligatorios() {
        when(usuarios.existePorCorreo(any())).thenReturn(false);
        when(usuarios.existePorIdentificacion(any())).thenReturn(false);
        when(codificador.encode(any())).thenReturn("hash");

        CrearUsuario sinInmueble = new CrearUsuario("RESIDENTE", 1L, "X", null, "x@mail.com", "Clave12345", null, null, null);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(sinInmueble))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("inmuebleId");

        CrearUsuario raro = new CrearUsuario("PRESIDENTE", 1L, "X", null, "x@mail.com", "Clave12345", null, null, null);
        assertThatThrownBy(() -> servicio.gestionarCuentaUsuario(raro)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    @DisplayName("modificarActive alterna el estado y, al desactivar, cierra la sesion del usuario")
    void modificarActive() {
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(vista(2L, "RESIDENTE", true)));

        UsuarioResponses.EstadoActivo resultado = servicio.modificarActive(1001L);

        assertThat(resultado.active()).isFalse();
        verify(usuarios).actualizarActive(2L, false);
        verify(usuarios).actualizarToken(2L, null);
    }

    @Test
    @DisplayName("modificarActive activa a un usuario inactivo sin tocar su token")
    void activar() {
        when(usuarios.buscarCredencialesPorIdentificacion(1001L)).thenReturn(Optional.of(vista(2L, "VIGILANTE", false)));

        assertThat(servicio.modificarActive(1001L).active()).isTrue();

        verify(usuarios).actualizarActive(2L, true);
        verify(usuarios, never()).actualizarToken(any(), any());
    }

    @Test
    @DisplayName("un administrador no se puede activar ni desactivar, y el usuario debe existir")
    void modificarActiveRechazos() {
        when(usuarios.buscarCredencialesPorIdentificacion(1L)).thenReturn(Optional.of(vista(1L, "ADMINISTRADOR", true)));
        when(usuarios.buscarCredencialesPorIdentificacion(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.modificarActive(1L)).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.modificarActive(404L)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(usuarios, never()).actualizarActive(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("listar valida el tipo de usuario")
    void listar() {
        assertThatThrownBy(() -> servicio.listar("PRESIDENTE", null)).isInstanceOf(SolicitudInvalidaException.class);

        when(usuarios.listar("RESIDENTE", true)).thenReturn(java.util.List.of());
        assertThat(servicio.listar(" residente ", true)).isEmpty();
    }
}
