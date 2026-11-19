package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.UsuarioGestionService;

import java.util.Map;

/** El administrador crea cuentas, verifica su correo con el codigo de 4 cifras y autoriza o bloquea usuarios. */
@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final UsuarioGestionService usuarios;

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
}
