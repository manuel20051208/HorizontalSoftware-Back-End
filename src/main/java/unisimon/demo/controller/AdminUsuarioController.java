package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.security.UsuarioAutenticado;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.RelacionInmuebleService;
import unisimon.demo.service.UsuarioGestionService;

import java.util.Map;

/** El administrador crea cuentas, verifica su correo, autoriza o bloquea usuarios y gestiona sus inmuebles. */
@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final UsuarioGestionService usuarios;
    private final RelacionInmuebleService relaciones;

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponses.Usuario crear(@Valid @RequestBody UsuarioRequests.CrearUsuario solicitud) {
        return usuarios.gestionarCuentaUsuario(solicitud);
    }

    @SoloAdministrador
    @GetMapping
    public Pagina<UsuarioResponses.Usuario> listar(@RequestParam(required = false) String tipo,
                                                   @RequestParam(required = false) Boolean active,
                                                   SolicitudPagina pagina) {
        return usuarios.listar(tipo, active, pagina);
    }

    @SoloAdministrador
    @GetMapping("/{identificacion}")
    public UsuarioResponses.Usuario consultar(@PathVariable Long identificacion) {
        return usuarios.consultar(identificacion);
    }

    @SoloAdministrador
    @PutMapping("/{identificacion}")
    public UsuarioResponses.Usuario actualizar(@PathVariable Long identificacion,
                                               @Valid @RequestBody UsuarioRequests.ActualizarUsuario solicitud) {
        return usuarios.actualizar(identificacion, solicitud);
    }

    @SoloAdministrador
    @DeleteMapping("/{identificacion}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(UsuarioAutenticado usuario, @PathVariable Long identificacion) {
        usuarios.eliminar(usuario.id(), identificacion);
    }

    /** Confirma el codigo que llego al correo del usuario: el correo queda verificado y la cuenta activa. */
    @SoloAdministrador
    @PostMapping("/{identificacion}/verificar-correo")
    public UsuarioResponses.Usuario verificarCorreo(@PathVariable Long identificacion,
                                                   @Valid @RequestBody UsuarioRequests.VerificarCorreo solicitud) {
        return usuarios.verificarCorreo(identificacion, solicitud.codigo());
    }

    /** Envia un codigo nuevo al correo de la cuenta; el anterior deja de servir. */
    @SoloAdministrador
    @PostMapping("/{identificacion}/reenviar-codigo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> reenviarCodigo(@PathVariable Long identificacion) {
        String correo = usuarios.reenviarCodigo(identificacion);
        return Map.of("mensaje", "Se envio un codigo nuevo a " + correo);
    }

    @SoloAdministrador
    @PatchMapping("/{identificacion}/activo")
    public UsuarioResponses.EstadoActivo modificarActive(@PathVariable Long identificacion) {
        return usuarios.modificarActive(identificacion);
    }

    // ------------------------------------------------------------------ relacion N:M con los inmuebles

    @SoloAdministrador
    @GetMapping("/{identificacion}/inmuebles")
    public UsuarioResponses.UsuarioInmuebles consultarInmuebles(UsuarioAutenticado usuario,
                                                                @PathVariable Long identificacion) {
        return relaciones.consultar(usuario.id(), identificacion);
    }

    /** Agrega un inmueble a la cuenta; no la reactiva ni verifica su correo. */
    @SoloAdministrador
    @PostMapping("/{identificacion}/inmuebles")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponses.UsuarioInmuebles vincularInmueble(UsuarioAutenticado usuario,
                                                             @PathVariable Long identificacion,
                                                             @Valid @RequestBody UsuarioRequests.RelacionInmueble s) {
        return relaciones.vincular(usuario.id(), identificacion, s.inmuebleId());
    }

    /** Mudanza del inmueble de la ruta al inmueble del cuerpo. */
    @SoloAdministrador
    @PutMapping("/{identificacion}/inmuebles/{inmuebleId}")
    public UsuarioResponses.UsuarioInmuebles mudarInmueble(UsuarioAutenticado usuario,
                                                          @PathVariable Long identificacion,
                                                          @PathVariable Long inmuebleId,
                                                          @Valid @RequestBody UsuarioRequests.RelacionInmueble s) {
        return relaciones.mudar(usuario.id(), identificacion, inmuebleId, s.inmuebleId());
    }

    /** Quita ese inmueble; si la cuenta se queda sin ninguno y estaba activa, pierde su sesion. */
    @SoloAdministrador
    @DeleteMapping("/{identificacion}/inmuebles/{inmuebleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincularInmueble(UsuarioAutenticado usuario, @PathVariable Long identificacion,
                                   @PathVariable Long inmuebleId) {
        relaciones.desvincular(usuario.id(), identificacion, inmuebleId);
    }
}
