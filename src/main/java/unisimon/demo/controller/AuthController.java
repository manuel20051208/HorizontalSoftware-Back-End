package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.AuthRequests;
import unisimon.demo.dto.response.AuthResponses;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.Autenticado;
import unisimon.demo.security.annotation.Publico;
import unisimon.demo.service.AutenticacionService;
import unisimon.demo.service.CuentaService;

import java.util.Map;

/** Sesion (OAuth2: access + refresh token) y cuenta del propio usuario. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AutenticacionService autenticacion;
    private final CuentaService cuentas;

    @Publico
    @PostMapping("/login")
    public AuthResponses.Sesion login(@Valid @RequestBody AuthRequests.Login solicitud) {
        return autenticacion.iniciarSesion(solicitud.correo(), solicitud.contrasena());
    }

    @Publico
    @PostMapping("/refresh")
    public AuthResponses.Sesion refresh(@Valid @RequestBody AuthRequests.Refresh solicitud) {
        return autenticacion.renovarSesion(solicitud.refreshToken());
    }

    @Autenticado
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(UsuarioAutenticado usuario) {
        autenticacion.cerrarSesion(usuario.id());
    }

    @Publico
    @PostMapping("/activar")
    public UsuarioResponses.Activacion activar(@Valid @RequestBody AuthRequests.Activar solicitud) {
        return cuentas.activarCuenta(solicitud.correo(), solicitud.contrasena(), solicitud.codigo());
    }

    @Publico
    @PostMapping("/reenviar-codigo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> reenviarCodigo(@Valid @RequestBody AuthRequests.ReenviarCodigo solicitud) {
        cuentas.reenviarCodigo(solicitud.correo());
        return Map.of("mensaje", "Si la cuenta existe y esta pendiente de activar, recibira un codigo nuevo");
    }

    @Publico
    @PostMapping("/recuperar-contrasena")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recuperar(@Valid @RequestBody AuthRequests.Recuperar solicitud) {
        cuentas.recuperarContrasena(solicitud.correo());
        return Map.of("mensaje", "Si el correo esta registrado, recibira una contrasena temporal");
    }

    @Autenticado
    @PutMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(UsuarioAutenticado usuario, @Valid @RequestBody AuthRequests.CambioContrasena s) {
        cuentas.cambiarContrasena(usuario.id(), s.contrasenaActual(), s.contrasenaNueva());
    }

    @Autenticado
    @GetMapping("/me")
    public UsuarioResponses.Usuario me(UsuarioAutenticado usuario) {
        return cuentas.consultarCuenta(usuario.id());
    }
}
