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

/** El administrador crea cuentas, verifica su correo con el codigo de 4 cifras, autoriza o bloquea usuarios y
 * relaciona a los residentes con su inmueble. */
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

    // ------------------------------------------------------------------ relacion con el inmueble

    @SoloAdministrador
    @GetMapping("/{identificacion}/inmueble")
    public UsuarioResponses.RelacionInmueble consultarInmueble(UsuarioAutenticado usuario,
                                                               @PathVariable Long identificacion) {
        return relaciones.consultar(usuario.id(), identificacion);
    }

    /** Vincula la cuenta con un inmueble; la cuenta no se reactiva por esto. */
    @SoloAdministrador
    @PostMapping("/{identificacion}/inmueble")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponses.RelacionInmueble vincularInmueble(UsuarioAutenticado usuario,
                                                              @PathVariable Long identificacion,
                                                              @Valid @RequestBody UsuarioRequests.RelacionInmueble s) {
        return relaciones.vincular(usuario.id(), identificacion, s.inmuebleId());
    }

    @SoloAdministrador
    @PutMapping("/{identificacion}/inmueble")
    public UsuarioResponses.RelacionInmueble cambiarInmueble(UsuarioAutenticado usuario,
                                                             @PathVariable Long identificacion,
                                                             @Valid @RequestBody UsuarioRequests.RelacionInmueble s) {
        return relaciones.cambiar(usuario.id(), identificacion, s.inmuebleId());
    }

    /** Quita la cuenta del inmueble; si estaba activa queda inactiva y sin sesion. */
    @SoloAdministrador
    @DeleteMapping("/{identificacion}/inmueble")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincularInmueble(UsuarioAutenticado usuario, @PathVariable Long identificacion) {
        relaciones.desvincular(usuario.id(), identificacion);
    }
}
