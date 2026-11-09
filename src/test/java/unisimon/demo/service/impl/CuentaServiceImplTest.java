package unisimon.demo.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.exception.SolicitudInvalidaException;
import unisimon.demo.mail.CorreoSender;
import unisimon.demo.model.Usuario;
import unisimon.demo.policy.GeneradorContrasena;
import unisimon.demo.policy.PoliticaContrasena;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.support.Proyecciones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private GeneradorContrasena generador;
    @Mock
    private CorreoSender correo;

    private CuentaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Bogota"));
        servicio = new CuentaServiceImpl(usuarios, codificador, new PoliticaContrasena(), generador, correo, reloj);
    }

    private CredencialesView usuario(String tipo, boolean activo, LocalDate creacion) {
        return Proyecciones.de(CredencialesView.class, "id", 1L, "nombreCompleto", "Santiago",
                "correoElectronico", "s@mail.com", "contrasena", "hash", "tipoUsuario", tipo, "active", activo,
                "fechaCreacionCuenta", creacion, "notificacionLeida", false);
    }

    @Test
    @DisplayName("activarCuenta activa una cuenta recien creada con sus credenciales")
    void activar() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario("RESIDENTE", false, LocalDate.of(2026, 11, 5))));
        when(codificador.matches("clave", "hash")).thenReturn(true);

        UsuarioResponses.Activacion resultado = servicio.activarCuenta("s@mail.com", "clave");

        assertThat(resultado.active()).isTrue();
        verify(usuarios).actualizarActive(1L, true);
    }

    @Test
    @DisplayName("activarCuenta no repite el trabajo si la cuenta ya esta activa")
    void yaActiva() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario("RESIDENTE", true, LocalDate.of(2026, 11, 5))));
        when(codificador.matches("clave", "hash")).thenReturn(true);

        assertThat(servicio.activarCuenta("s@mail.com", "clave").mensaje()).contains("ya estaba activa");
        verify(usuarios, never()).actualizarActive(1L, true);
    }

    @Test
    @DisplayName("pasado el plazo de 7 dias solo la administracion puede activar la cuenta")
    void plazoVencido() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario("RESIDENTE", false, LocalDate.of(2026, 10, 1))));
        when(codificador.matches("clave", "hash")).thenReturn(true);

        assertThatThrownBy(() -> servicio.activarCuenta("s@mail.com", "clave"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("plazo");
        verify(usuarios, never()).actualizarActive(1L, true);
    }

    @Test
    @DisplayName("activarCuenta rechaza credenciales incorrectas y a los invitados")
    void activarRechazos() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario("RESIDENTE", false, LocalDate.of(2026, 11, 5))));
        when(codificador.matches("mala", "hash")).thenReturn(false);
        assertThatThrownBy(() -> servicio.activarCuenta("s@mail.com", "mala")).isInstanceOf(NoAutenticadoException.class);

        when(usuarios.buscarCredencialesPorCorreo("inv@mail.com"))
                .thenReturn(Optional.of(usuario(Usuario.USUARIO_AUTORIZADO, false, LocalDate.of(2026, 11, 5))));
        when(codificador.matches("clave", "hash")).thenReturn(true);
        assertThatThrownBy(() -> servicio.activarCuenta("inv@mail.com", "clave")).isInstanceOf(ProhibidoException.class);
    }

    @Test
    @DisplayName("recuperarContrasena genera una temporal, cierra sesiones y la envia por correo")
    void recuperar() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario("RESIDENTE", true, LocalDate.of(2026, 11, 5))));
        when(generador.generar()).thenReturn("Temporal123abc");
        when(codificador.encode("Temporal123abc")).thenReturn("hash-temporal");

        servicio.recuperarContrasena("s@mail.com");

        verify(usuarios).actualizarContrasena(1L, "hash-temporal");
        verify(usuarios).actualizarToken(1L, null);
        verify(correo).enviar(eq("s@mail.com"), anyString(), contains("Temporal123abc"));
    }

    @Test
    @DisplayName("recuperarContrasena no revela si el correo existe y no toca a los invitados")
    void recuperarSinRevelar() {
        when(usuarios.buscarCredencialesPorCorreo("nadie@mail.com")).thenReturn(Optional.empty());
        when(usuarios.buscarCredencialesPorCorreo("inv@mail.com"))
                .thenReturn(Optional.of(usuario(Usuario.USUARIO_AUTORIZADO, true, LocalDate.of(2026, 11, 5))));

        servicio.recuperarContrasena("nadie@mail.com");
        servicio.recuperarContrasena("inv@mail.com");

        verifyNoInteractions(correo);
        verify(usuarios, never()).actualizarContrasena(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("cambiarContrasena exige la actual, valida la nueva y cierra las sesiones abiertas")
    void cambiar() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, LocalDate.now())));
        when(codificador.matches("Actual1234", "hash")).thenReturn(true);
        when(codificador.encode("Nueva98765")).thenReturn("hash-nuevo");

        servicio.cambiarContrasena(1L, "Actual1234", "Nueva98765");

        verify(usuarios).actualizarContrasena(1L, "hash-nuevo");
        verify(usuarios).actualizarToken(1L, null);
    }

    @Test
    @DisplayName("cambiarContrasena rechaza la actual incorrecta y una nueva que no cumple la politica")
    void cambiarRechazos() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, LocalDate.now())));
        when(codificador.matches("mala", "hash")).thenReturn(false);
        when(codificador.matches("Actual1234", "hash")).thenReturn(true);

        assertThatThrownBy(() -> servicio.cambiarContrasena(1L, "mala", "Nueva98765"))
                .isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.cambiarContrasena(1L, "Actual1234", "corta"))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(usuarios, never()).actualizarContrasena(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    @DisplayName("consultarCuenta devuelve los datos del usuario o 401 si la sesion no existe")
    void consultarCuenta() {
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, LocalDate.now())));
        when(usuarios.buscarCredencialesPorId(99L)).thenReturn(Optional.empty());

        assertThat(servicio.consultarCuenta(1L).nombreCompleto()).isEqualTo("Santiago");
        assertThatThrownBy(() -> servicio.consultarCuenta(99L)).isInstanceOf(NoAutenticadoException.class);
    }
}
