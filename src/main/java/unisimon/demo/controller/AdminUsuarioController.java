package unisimon.demo.controller;

import jakarta.validation.Valid;
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
import unisimon.demo.dto.request.UsuarioRequests;
import unisimon.demo.dto.response.UsuarioResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.UsuarioGestionService;

import java.util.List;

/** El administrador crea cuentas y autoriza o bloquea usuarios. */
@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {

    private final UsuarioGestionService usuarios;

    public AdminUsuarioController(UsuarioGestionService usuarios) {
        this.usuarios = usuarios;
    }

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponses.Usuario crear(@Valid @RequestBody UsuarioRequests.CrearUsuario solicitud) {
        return usuarios.gestionarCuentaUsuario(solicitud);
    }

    @SoloAdministrador
    @GetMapping
    public List<UsuarioResponses.Usuario> listar(@RequestParam(required = false) String tipo,
                                                 @RequestParam(required = false) Boolean active) {
        return usuarios.listar(tipo, active);
    }

    @SoloAdministrador
    @PatchMapping("/{identificacion}/activo")
    public UsuarioResponses.EstadoActivo modificarActive(@PathVariable Long identificacion) {
        return usuarios.modificarActive(identificacion);
    }
}
