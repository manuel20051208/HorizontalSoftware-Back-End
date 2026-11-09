package unisimon.demo.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import unisimon.demo.dto.request.LiquidacionRequests;
import unisimon.demo.dto.response.InmuebleResponses;
import unisimon.demo.dto.response.LiquidacionResponses;
import unisimon.demo.security.annotation.SoloAdministrador;
import unisimon.demo.service.LiquidacionService;
import unisimon.demo.service.PanelAdministradorService;

import java.time.LocalDate;
import java.util.List;

/** Liquidaciones mensuales y resumen del panel del administrador. */
@RestController
@RequestMapping("/api/admin")
public class AdminLiquidacionController {

    private final LiquidacionService liquidaciones;
    private final PanelAdministradorService panel;

    public AdminLiquidacionController(LiquidacionService liquidaciones, PanelAdministradorService panel) {
        this.liquidaciones = liquidaciones;
        this.panel = panel;
    }

    @SoloAdministrador
    @GetMapping("/resumen")
    public InmuebleResponses.ResumenAdmin resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo) {
        return panel.resumen(periodo);
    }

    @SoloAdministrador
    @PostMapping("/liquidaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public LiquidacionResponses.Liquidacion generar(@Valid @RequestBody LiquidacionRequests.Generar solicitud) {
        return liquidaciones.generarLiquidacion(solicitud);
    }

    @SoloAdministrador
    @PostMapping("/liquidaciones/masiva")
    public LiquidacionResponses.ResultadoMasiva generarMasiva(
            @Valid @RequestBody LiquidacionRequests.GenerarMasiva solicitud) {
        return liquidaciones.generarMasiva(solicitud);
    }

    @SoloAdministrador
    @GetMapping("/liquidaciones")
    public List<LiquidacionResponses.Liquidacion> listar(
            @RequestParam(required = false) Long inmuebleId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo) {
        return liquidaciones.listar(inmuebleId, estado, periodo);
    }

    @SoloAdministrador
    @PatchMapping("/liquidaciones/{id}/mora")
    public LiquidacionResponses.Liquidacion marcarEnMora(@PathVariable Long id) {
        return liquidaciones.marcarEnMora(id);
    }

    @SoloAdministrador
    @PatchMapping("/liquidaciones/{id}/al-dia")
    public LiquidacionResponses.Liquidacion marcarAlDia(@PathVariable Long id) {
        return liquidaciones.marcarAlDia(id);
    }
}
