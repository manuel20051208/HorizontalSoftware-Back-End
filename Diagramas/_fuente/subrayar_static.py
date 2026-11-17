"""Subraya (UML: miembros de clase / static) las lineas indicadas del diagrama de clases."""
import re, sys
ruta = sys.argv[1]
STATIC = [
    "+ PENDIENTE : String", "+ AL_DIA : String", "+ EN_MORA : String", "+ DIA_LIMITE_POR_DEFECTO : int",
    "- nz(v : BigDecimal) : BigDecimal",
    "+ INCIDENCIA : String", "- pesos(valor : BigDecimal) : String", "- cuando(reserva : ReservaZonaComun) : String",
    "+ APROBADO : String", "+ RECHAZADO : String", "+ ANULADO : String", "+ REEMBOLSADO : String",
    "+ FECHA_BASE : LocalDate",
    "+ ADMINISTRADOR : String", "+ RESIDENTE : String", "+ VIGILANTE : String", "+ USUARIO_AUTORIZADO : String",
    "- liquidacionVigente(inmueble : Inmueble) : Liquidacion",
    "+ PROGRAMADA : String", "+ EN_CURSO : String", "+ FINALIZADA : String",
    "+ ACTIVA : String", "+ INACTIVA : String", "- MONEDA : String",
    "~ firmaIntegridad(referencia : String, centavos : long, secretoIntegridad : String) : String",
    "- enc(v : String) : String",
]
txt = open(ruta, encoding="utf-8").read()
total = 0
def fix(m):
    global total
    partes = m.group(1).split("&lt;br&gt;")
    for i, p in enumerate(partes):
        if p.strip() in STATIC:
            partes[i] = "&lt;u&gt;" + p + "&lt;/u&gt;"; total += 1
    return 'value="' + "&lt;br&gt;".join(partes) + '"'
txt = re.sub(r'value="([^"]*)"', fix, txt)
open(ruta, "w", encoding="utf-8", newline="").write(txt)
print("subrayadas:", total)
