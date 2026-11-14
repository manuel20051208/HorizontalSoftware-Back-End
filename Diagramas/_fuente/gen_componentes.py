"""Diagrama de componentes UML del backend (interfaces provistas = bola, requeridas = socket).
Genera componentes-backend.xml (draw.io) y componentes-backend.svg (para la captura PNG)."""
import sys, os, math, base64
from xml.sax.saxutils import escape

OUT = sys.argv[1]
PAGE_W, PAGE_H = 1720, 1230
q = {'"': "&quot;"}

# tipo: int (interno), core (servicios), ext (externo), db
COMP = {
    "front": ("ext", "«componente externo»", "Front-end web", ["software-residencial", "(Next.js)"], (40, 200, 230, 130)),
    "api": ("int", "«component»", "API REST", ["controller", "16 controladores REST"], (460, 210, 260, 130)),
    "seg": ("int", "«component»", "Seguridad", ["security · security.annotation · config", "JwtTokenService, SecurityConfig"], (930, 210, 300, 130)),
    "sch": ("int", "«component»", "Scheduler", ["scheduler", "LiquidacionScheduler"], (380, 500, 140, 110)),
    "srv": ("core", "«component»", "Servicios de negocio", ["service · service.impl · policy", "mapper · factory · service.support", "cache"], (660, 480, 330, 150)),
    "wom": ("int", "«component»", "Integración Wompi", ["integration.wompi", "ClienteWompiRest", "FirmaEventosWompi"], (1070, 490, 210, 130)),
    "per": ("int", "«component»", "Persistencia", ["repository · 14 *Repository", "Spring Data + SQL nativo"], (480, 770, 260, 120)),
    "cor": ("int", "«component»", "Correo", ["mail", "CorreoLogSender (simulado)"], (820, 770, 200, 120)),
    "boo": ("int", "«component»", "Bootstrap", ["bootstrap", "DatosIniciales", "SincronizacionPasarela"], (1080, 770, 200, 120)),
    "wext": ("ext", "«sistema externo»", "Wompi", ["pasarela de pagos"], (1420, 480, 240, 150)),
    "pg": ("db", "«base de datos»", "PostgreSQL 16", ["esquema migrado con Flyway"], (480, 1060, 260, 120)),
}
BOUND = (320, 105, 990, 890)
BOUND_LABEL = "«subsystem» HorizontalSoftware-Back-End — Spring Boot (paquete unisimon.demo)"

# conectores de ensamblaje:
#  proveedor: punto en el borde del componente que provee, bola B
#  requeridor: polilinea desde el borde del que requiere hasta B (se recorta en el socket)
#  etiqueta: (texto-lineas, x, y, ancla)
ASM = [
    dict(p=(460, 265), b=(430, 265), r=[(270, 265), (430, 265)],
         label=(["API REST /api/**", "JSON + JWT Bearer"], 400, 292, "middle")),
    dict(p=(930, 265), b=(900, 265), r=[(720, 265), (900, 265)],
         label=(["UsuarioAutenticado", "filtro JWT · roles (@Rol)"], 810, 236, "middle")),
    dict(p=(650, 210), b=(650, 185), r=[(1500, 480), (1500, 160), (650, 160), (650, 185)],
         label=(["Webhook /api/pasarela/webhook"], 666, 196, "start")),
    dict(p=(1610, 480), b=(1610, 450), r=[(155, 200), (155, 85), (1610, 85), (1610, 450)],
         label=(["Web Checkout"], 1594, 456, "end")),
    dict(p=(690, 480), b=(690, 450), r=[(690, 340), (690, 450)],
         label=(["*Service (16 interfaces)"], 706, 455, "start")),
    dict(p=(660, 555), b=(630, 555), r=[(520, 555), (630, 555)],
         label=(["LiquidacionService"], 585, 536, "middle")),
    dict(p=(960, 340), b=(960, 370), r=[(960, 480), (960, 370)],
         label=(["TokenService"], 976, 375, "start")),
    dict(p=(1070, 555), b=(1040, 555), r=[(990, 555), (1040, 555)],
         label=(["ClienteWompi"], 1030, 536, "middle")),
    dict(p=(1420, 555), b=(1390, 555), r=[(1280, 555), (1390, 555)],
         label=(["API REST de", "transacciones"], 1352, 522, "middle")),
    dict(p=(700, 770), b=(700, 740), r=[(700, 630), (700, 740)],
         label=(["*Repository"], 684, 745, "end")),
    dict(p=(480, 830), b=(450, 830), r=[(460, 325), (350, 325), (350, 830), (450, 830)],
         label=(["*Repository"], 468, 856, "end")),
    dict(p=(920, 770), b=(920, 740), r=[(920, 630), (920, 740)],
         label=(["CorreoSender"], 936, 745, "start")),
    dict(p=(680, 890), b=(680, 920), r=[(1180, 890), (1180, 950), (680, 950), (680, 920)],
         label=(["*Repository"], 696, 925, "start")),
    dict(p=(580, 1060), b=(580, 1030), r=[(580, 890), (580, 1030)],
         label=(["JDBC (HikariCP)"], 596, 1035, "start")),
]
RB, RS = 8, 14   # radio bola, radio socket

TITLE = "Diagrama de componentes — HorizontalSoftware-Back-End"
NOTE = ("○ interfaz provista   ◡ interfaz requerida (socket).  "
        "Cada conector une el componente que la requiere con el que la implementa.")

COLORS = {"int": ("#dae8fc", "#6c8ebf"), "core": ("#d5e8d4", "#82b366"),
          "ext": ("#f5f5f5", "#666666"), "db": ("#e1d5e7", "#9673a6")}

def unit(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy)
    return dx / n, dy / n

def socket_geom(a):
    """devuelve (d_prov, puntos del arco, linea del requeridor recortada)."""
    B = a["b"]
    dp = unit(B, a["p"])                      # hacia el proveedor
    perp = (-dp[1], dp[0])
    p1 = (B[0] + RS * perp[0], B[1] + RS * perp[1])
    p2 = (B[0] - RS * perp[0], B[1] - RS * perp[1])
    pts = list(a["r"])
    apex = (B[0] - RS * dp[0], B[1] - RS * dp[1])
    pts[-1] = apex
    return dp, p1, p2, pts

# ---------------- SVG ----------------
s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{PAGE_W}" height="{PAGE_H}" viewBox="0 0 {PAGE_W} {PAGE_H}" '
     f'font-family="Segoe UI, Helvetica, Arial, sans-serif">',
     f'<rect width="{PAGE_W}" height="{PAGE_H}" fill="#fff"/>',
     f'<text x="40" y="38" font-size="20" font-weight="bold" fill="#111">{escape(TITLE)}</text>',
     f'<text x="40" y="62" font-size="13" fill="#444">{escape(NOTE)}</text>']
bx, by, bw, bh = BOUND
s.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="#fbfcfe" stroke="#7a8aa6" stroke-width="1.4" stroke-dasharray="8 4"/>')
s.append(f'<text x="{bx + 14}" y="{by + 24}" font-size="13" font-weight="bold" fill="#33415c">{escape(BOUND_LABEL)}</text>')

LINE = "#3d3d3d"
for a in ASM:
    dp, p1, p2, pts = socket_geom(a)
    B = a["b"]
    s.append(f'<line x1="{a["p"][0]}" y1="{a["p"][1]}" x2="{B[0] + RB * dp[0]}" y2="{B[1] + RB * dp[1]}" stroke="{LINE}" stroke-width="1.4"/>')
    s.append(f'<polyline points="{" ".join(f"{x},{y}" for x, y in pts)}" fill="none" stroke="{LINE}" stroke-width="1.4"/>')
    s.append(f'<circle cx="{B[0]}" cy="{B[1]}" r="{RB}" fill="#fff" stroke="{LINE}" stroke-width="1.6"/>')
    s.append(f'<path d="M{p1[0]:.1f},{p1[1]:.1f} A{RS},{RS} 0 0 1 {p2[0]:.1f},{p2[1]:.1f}" fill="none" stroke="{LINE}" stroke-width="1.6"/>')
    lines, lx, ly, anc = a["label"]
    for k, t in enumerate(lines):
        s.append(f'<text x="{lx}" y="{ly + k * 14}" text-anchor="{anc}" font-size="11.5" fill="#1a1a1a" '
                 f'paint-order="stroke" stroke="#fff" stroke-width="3">{escape(t)}</text>')

def comp_icon(x, y):
    return (f'<rect x="{x}" y="{y}" width="16" height="20" fill="#fff" stroke="#555" stroke-width="1"/>'
            f'<rect x="{x - 4}" y="{y + 4}" width="8" height="4" fill="#fff" stroke="#555" stroke-width="1"/>'
            f'<rect x="{x - 4}" y="{y + 12}" width="8" height="4" fill="#fff" stroke="#555" stroke-width="1"/>')

for k, (kind, stereo, name, det, (x, y, w, h)) in COMP.items():
    f, st = COLORS[kind]
    cx = x + w / 2
    if kind == "db":
        e = 14
        s.append(f'<path d="M{x},{y + e} A{w / 2},{e} 0 0 1 {x + w},{y + e} V{y + h - e} A{w / 2},{e} 0 0 1 {x},{y + h - e} Z" '
                 f'fill="{f}" stroke="{st}" stroke-width="1.4"/>')
        s.append(f'<path d="M{x},{y + e} A{w / 2},{e} 0 0 0 {x + w},{y + e}" fill="none" stroke="{st}" stroke-width="1.4"/>')
        ty = y + 2 * e + 18
    else:
        s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{f}" stroke="{st}" stroke-width="1.4"/>')
        s.append(comp_icon(x + w - 26, y + 10))
        ty = y + 26
    s.append(f'<text x="{cx}" y="{ty}" text-anchor="middle" font-size="11" fill="#555">{escape(stereo)}</text>')
    s.append(f'<text x="{cx}" y="{ty + 20}" text-anchor="middle" font-size="15" font-weight="bold" fill="#111">{escape(name)}</text>')
    for i, t in enumerate(det):
        s.append(f'<text x="{cx}" y="{ty + 40 + i * 15}" text-anchor="middle" font-size="11" fill="#333">{escape(t)}</text>')
s.append("</svg>")
open(os.path.join(OUT, "componentes-backend.svg"), "w", encoding="utf-8").write("\n".join(s))

# ---------------- draw.io ----------------
cells = []
n = [0]
def nid(p):
    n[0] += 1
    return f"{p}{n[0]}"

def text_cell(html, x, y, w, h, style_extra=""):
    cells.append(f'<mxCell id="{nid("t")}" value="{escape(html, q)}" style="text;html=1;{style_extra}" vertex="1" '
                 f'parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')

def edge(points, w=1.4):
    (sx, sy), (tx, ty) = points[0], points[-1]
    mid = "".join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in points[1:-1])
    arr = f'<Array as="points">{mid}</Array>' if mid else ""
    cells.append(f'<mxCell id="{nid("e")}" value="" style="endArrow=none;html=1;rounded=0;strokeColor={LINE};'
                 f'strokeWidth={w};edgeStyle=none;" edge="1" parent="1"><mxGeometry relative="1" as="geometry">'
                 f'<mxPoint x="{sx}" y="{sy}" as="sourcePoint"/><mxPoint x="{tx}" y="{ty}" as="targetPoint"/>{arr}'
                 f'</mxGeometry></mxCell>')

text_cell(f"<b>{TITLE}</b>", 40, 18, 900, 28, "fontSize=18;align=left;verticalAlign=middle;")
text_cell(NOTE, 40, 48, 1200, 24, "fontSize=12;align=left;verticalAlign=middle;fontColor=#444444;")
cells.append(f'<mxCell id="bound" value="{escape("<b>" + BOUND_LABEL + "</b>", q)}" style="rounded=0;html=1;'
             f'dashed=1;dashPattern=8 4;fillColor=#fbfcfe;strokeColor=#7a8aa6;verticalAlign=top;align=left;'
             f'spacingLeft=12;spacingTop=4;fontSize=13;fontColor=#33415c;" vertex="1" parent="1">'
             f'<mxGeometry x="{bx}" y="{by}" width="{bw}" height="{bh}" as="geometry"/></mxCell>')
for k, (kind, stereo, name, det, (x, y, w, h)) in COMP.items():
    f, st = COLORS[kind]
    html = (f'<font style="font-size:11px" color="#555555">{stereo}</font><br><b style="font-size:15px">{name}</b><br>'
            f'<font style="font-size:11px">{"<br>".join(det)}</font>')
    if kind == "db":
        style = f"shape=cylinder3;boundedLbl=1;size=14;whiteSpace=wrap;html=1;fillColor={f};strokeColor={st};"
    else:
        style = f"rounded=0;whiteSpace=wrap;html=1;fillColor={f};strokeColor={st};verticalAlign=middle;"
    cells.append(f'<mxCell id="c_{k}" value="{escape(html, q)}" style="{style}" vertex="1" parent="1">'
                 f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
    if kind != "db":
        cells.append(f'<mxCell id="i_{k}" value="" style="shape=component;jettyWidth=8;jettyHeight=4;html=1;'
                     f'fillColor=#ffffff;strokeColor=#555555;" vertex="1" parent="c_{k}">'
                     f'<mxGeometry x="{w - 30}" y="10" width="20" height="20" as="geometry"/></mxCell>')

def socket_img(dp):
    c = 16
    perp = (-dp[1], dp[0])
    p1 = (c + RS * perp[0], c + RS * perp[1]); p2 = (c - RS * perp[0], c - RS * perp[1])
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">'
           f'<path d="M{p1[0]:.1f},{p1[1]:.1f} A{RS},{RS} 0 0 1 {p2[0]:.1f},{p2[1]:.1f}" fill="none" '
           f'stroke="{LINE}" stroke-width="1.6"/></svg>')
    return base64.b64encode(svg.encode()).decode()

for a in ASM:
    dp, p1, p2, pts = socket_geom(a)
    B = a["b"]
    edge([a["p"], (B[0] + RB * dp[0], B[1] + RB * dp[1])])
    edge(pts)
    cells.append(f'<mxCell id="{nid("b")}" value="" style="ellipse;html=1;fillColor=#ffffff;strokeColor={LINE};'
                 f'strokeWidth=1.6;" vertex="1" parent="1"><mxGeometry x="{B[0] - RB}" y="{B[1] - RB}" '
                 f'width="{2 * RB}" height="{2 * RB}" as="geometry"/></mxCell>')
    cells.append(f'<mxCell id="{nid("s")}" value="" style="shape=image;html=1;imageAspect=0;aspect=fixed;'
                 f'image=data:image/svg+xml,{socket_img(dp)};" vertex="1" parent="1"><mxGeometry x="{B[0] - 16}" '
                 f'y="{B[1] - 16}" width="32" height="32" as="geometry"/></mxCell>')
    lines, lx, ly, anc = a["label"]
    wlab = max(len(t) for t in lines) * 7 + 10
    hl = 15 * len(lines)
    x0 = {"start": lx, "middle": lx - wlab / 2, "end": lx - wlab}[anc]
    al = {"start": "left", "middle": "center", "end": "right"}[anc]
    text_cell("<br>".join(escape(t) for t in lines), x0, ly - 12, wlab, hl,
              f"fontSize=11;align={al};verticalAlign=top;labelBackgroundColor=#ffffff;spacing=0;")

xml = ('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n'
       '  <diagram name="Componentes" id="componentes-backend">\n'
       f'    <mxGraphModel grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" '
       f'pageScale="1" pageWidth="{PAGE_W}" pageHeight="{PAGE_H}" math="0" shadow="0">\n      <root>\n'
       '        <mxCell id="0"/>\n        <mxCell id="1" parent="0"/>\n        '
       + "\n        ".join(cells) + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n")
open(os.path.join(OUT, "componentes-backend.xml"), "w", encoding="utf-8").write(xml)
print(PAGE_W, PAGE_H)
