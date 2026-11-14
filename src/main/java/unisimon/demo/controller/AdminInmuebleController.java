package unisimon.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unisimon.demo.dto.request.InmuebleRequests;
import unisimon.demo.dto.request.PagoRequests;
import unisimon.demo.dto.request.SolicitudPagina;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.dto.response.NotificacionResponses;
import unisimon.demo.dto.response.Pagina;
import unisimon.demo.dto.response.PagoResponses;
import unisimon.demo.dto.response.ZonaResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.InmuebleService;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.NotificacionService;
import unisimon.demo.service.PagoService;
import unisimon.demo.service.ZonaComunService;

import java.util.List;

/** Inmuebles y todo lo que el administrador configura sobre uno de ellos. */
@RestController
@RequestMapping("/api/admin/inmuebles")
@RequiredArgsConstructor
public class AdminInmuebleController {

    private final InmuebleService inmuebles;
    private final LiquidacionService liquidaciones;
    private final ZonaComunService zonas;
    private final PagoService pagos;
    private final NotificacionService notificaciones;

    @SoloAdministrador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InmuebleResponses.Detalle crear(@Valid @RequestBody InmuebleRequests.CrearInmueble solicitud) {
        return inmuebles.crear(solicitud);
    }

    @SoloAdministrador
    @GetMapping
    public Pagina<InmuebleResponses.Resumen> listar(SolicitudPagina pagina) {
        return inmuebles.listar(pagina);
    }

    @SoloAdministrador
    @GetMapping("/{id}")
    public InmuebleResponses.Detalle detalle(@PathVariable Long id) {
        return inmuebles.detalle(id);
    }

    @SoloAdministrador
    @PutMapping("/{id}/tarifas")
    public LiquidacionResponses.Liquidacion configurarTarifas(@PathVariable Long id,
                                                              @Valid @RequestBody InmuebleRequests.Tarifas tarifas) {
        return liquidaciones.configurarTarifas(id, tarifas);
    }

    @SoloAdministrador
    @PutMapping("/{id}/multas")
    public LiquidacionResponses.Liquidacion configurarMultas(@PathVariable Long id,
                                                             @Valid @RequestBody InmuebleRequests.Multas multas) {
        return liquidaciones.configurarMultas(id, multas);
    }

    @SoloAdministrador
    @PutMapping("/{id}/zonas-comunes")
    public List<ZonaResponses.Zona> configurarZonasComunes(@PathVariable Long id,
                                                           @Valid @RequestBody InmuebleRequests.AsignarZonas s) {
        return zonas.configurarZonasComunes(id, s.zonaComunIds());
    }

    @SoloAdministrador
    @PostMapping("/{id}/recordatorio-pago")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificacionResponses.Notificacion recordatorioPago(@PathVariable Long id) {
        return notificaciones.notificarPagoPendiente(id);
    }

    @SoloAdministrador
    @PostMapping("/{id}/conciliar-pago")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponses.Pago conciliarPago(@PathVariable Long id, @Valid @RequestBody PagoRequests.Conciliar s) {
        return pagos.conciliarPagosAdministracion(id, s);
    }
}
