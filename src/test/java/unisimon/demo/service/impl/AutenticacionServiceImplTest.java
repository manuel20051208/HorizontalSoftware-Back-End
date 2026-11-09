package unisimon.demo.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.exception.NoAutenticadoException;
import unisimon.demo.exception.ProhibidoException;
import unisimon.demo.model.Usuario;
import unisimon.demo.repository.UsuarioRepository;
import unisimon.demo.repository.UsuarioRepository.CredencialesView;
import unisimon.demo.security.IdentidadToken;
import unisimon.demo.security.ParTokens;
import unisimon.demo.security.TokenService;
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
class AutenticacionServiceImplTest {

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private TokenService tokens;
    @InjectMocks
    private AutenticacionServiceImpl servicio;

    private CredencialesView usuario(String tipo, boolean activo, String huellaGuardada) {
        return Proyecciones.de(CredencialesView.class, "id", 1L, "identificacion", 100L,
                "nombreCompleto", "Santiago Mendez", "correoElectronico", "s@mail.com", "contrasena", "hash",
                "tipoUsuario", tipo, "active", activo, "token", huellaGuardada, "inmuebleId", 5L,
                "notificacionLeida", false, "fechaCreacionCuenta", LocalDate.of(2026, 11, 1));
    }

    private ParTokens par(String access, String refresh) {
        return new ParTokens(access, refresh, 900, "Bearer");
    }

    @Test
    @DisplayName("iniciarSesion con credenciales validas entrega tokens y guarda solo la huella del refresh")
    void iniciarSesion() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com")).thenReturn(Optional.of(usuario("RESIDENTE", true, null)));
        when(codificador.matches("clave", "hash")).thenReturn(true);
        when(tokens.emitir(any(IdentidadToken.class))).thenReturn(par("acc", "ref"));
        when(tokens.huella("ref")).thenReturn("huella-ref");

        AuthResponses.Sesion sesion = servicio.iniciarSesion(" s@mail.com ", "clave");

        assertThat(sesion.accessToken()).isEqualTo("acc");
        assertThat(sesion.refreshToken()).isEqualTo("ref");
        assertThat(sesion.tokenType()).isEqualTo("Bearer");
        assertThat(sesion.expiresIn()).isEqualTo(900);
        assertThat(sesion.usuario().nombreCompleto()).isEqualTo("Santiago Mendez");
        ArgumentCaptor<IdentidadToken> identidad = ArgumentCaptor.forClass(IdentidadToken.class);
        verify(tokens).emitir(identidad.capture());
        assertThat(identidad.getValue()).isEqualTo(new IdentidadToken(1L, "RESIDENTE", "Santiago Mendez"));
        verify(usuarios).actualizarToken(1L, "huella-ref");
    }

    @Test
    @DisplayName("contrasena incorrecta o correo desconocido dan el mismo 401")
    void credencialesInvalidas() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com")).thenReturn(Optional.of(usuario("RESIDENTE", true, null)));
        when(codificador.matches("mala", "hash")).thenReturn(false);
        when(usuarios.buscarCredencialesPorCorreo("nadie@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciarSesion("s@mail.com", "mala"))
                .isInstanceOf(NoAutenticadoException.class).hasMessage("Correo o contrasena incorrectos");
        assertThatThrownBy(() -> servicio.iniciarSesion("nadie@mail.com", "x"))
                .isInstanceOf(NoAutenticadoException.class).hasMessage("Correo o contrasena incorrectos");
        verify(usuarios, never()).actualizarToken(any(), any());
    }

    @Test
    @DisplayName("una cuenta inactiva no inicia sesion")
    void cuentaInactiva() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com")).thenReturn(Optional.of(usuario("RESIDENTE", false, null)));
        when(codificador.matches("clave", "hash")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("s@mail.com", "clave"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("no esta activa");
        verify(tokens, never()).emitir(any());
    }

    @Test
    @DisplayName("un invitado (UsuarioAutorizado) nunca inicia sesion")
    void invitadoNoInicia() {
        when(usuarios.buscarCredencialesPorCorreo("s@mail.com"))
                .thenReturn(Optional.of(usuario(Usuario.USUARIO_AUTORIZADO, true, null)));
        when(codificador.matches("clave", "hash")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("s@mail.com", "clave"))
                .isInstanceOf(ProhibidoException.class).hasMessageContaining("invitados");
    }

    @Test
    @DisplayName("renovarSesion rota el refresh token: guarda la huella del nuevo")
    void renovarSesion() {
        when(tokens.leerRefresh("ref1")).thenReturn(new IdentidadToken(1L, "RESIDENTE", "Santiago Mendez"));
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, "huella-1")));
        when(tokens.huella("ref1")).thenReturn("huella-1");
        when(tokens.emitir(any(IdentidadToken.class))).thenReturn(par("acc2", "ref2"));
        when(tokens.huella("ref2")).thenReturn("huella-2");

        AuthResponses.Sesion sesion = servicio.renovarSesion("ref1");

        assertThat(sesion.accessToken()).isEqualTo("acc2");
        assertThat(sesion.refreshToken()).isEqualTo("ref2");
        verify(usuarios).actualizarToken(1L, "huella-2");
    }

    @Test
    @DisplayName("un refresh token ya rotado corta la sesion (se borra la huella) y se rechaza")
    void refreshReutilizado() {
        when(tokens.leerRefresh("ref-viejo")).thenReturn(new IdentidadToken(1L, "RESIDENTE", "Santiago Mendez"));
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, "huella-actual")));
        when(tokens.huella("ref-viejo")).thenReturn("huella-vieja");

        assertThatThrownBy(() -> servicio.renovarSesion("ref-viejo")).isInstanceOf(NoAutenticadoException.class);

        verify(usuarios).actualizarToken(1L, null);
        verify(tokens, never()).emitir(any());
    }

    @Test
    @DisplayName("renovarSesion falla si la sesion fue cerrada (no hay huella guardada)")
    void refreshTrasCerrarSesion() {
        when(tokens.leerRefresh("ref")).thenReturn(new IdentidadToken(1L, "RESIDENTE", "S"));
        when(usuarios.buscarCredencialesPorId(1L)).thenReturn(Optional.of(usuario("RESIDENTE", true, null)));
        when(tokens.huella("ref")).thenReturn("h");

        assertThatThrownBy(() -> servicio.renovarSesion("ref")).isInstanceOf(NoAutenticadoException.class);
    }

    @Test
    @DisplayName("cerrarSesion revoca el refresh token del usuario")
    void cerrarSesion() {
        servicio.cerrarSesion(1L);

        verify(usuarios).actualizarToken(1L, null);
    }
}
