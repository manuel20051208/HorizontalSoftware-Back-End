"""Reorganiza el diagrama de clases (draw.io) para que ninguna flecha cruce a otra ni pase por encima de una clase.

Toma el CONTENIDO (nombre, atributos, operaciones, relaciones y multiplicidades) del XML de entrada y lo vuelve a
escribir con una distribucion en rejilla y conectores ortogonales con puntos de salida/llegada fijos:
  - cada flecha queda pegada a su clase origen y destino (si se mueve una caja en draw.io, la flecha la sigue);
  - las flechas rectas no llevan puntos intermedios; las que doblan llevan solo sus esquinas, en carriles propios.
No subraya nada (sin <u>).

Uso: python gen_diagrama_clases.py [entrada.xml] [salida.xml]
     por defecto lee y escribe Diagramas/Clases/DiagramaClasesSoftwareHorizontal.xml
"""
import html
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
XML_DEF = os.path.join(RAIZ, "Diagramas", "Clases", "DiagramaClasesSoftwareHorizontal.xml")
ENTRADA = sys.argv[1] if len(sys.argv) > 1 else XML_DEF
SALIDA = sys.argv[2] if len(sys.argv) > 2 else XML_DEF

CHAR = 6.9          # ancho medio de un caracter a 12 px, para elegir el ancho de la caja
CHAR_CORTE = 5.9    # para estimar cuantas lineas ocupa un texto largo al partirse
LINEA = 14.6        # alto de una linea de texto
CABECERA = 26
SEPARADOR = 8
PAD = 10            # aire bajo cada compartimento
ANCHO_MIN, ANCHO_MAX = 230, 430
GX, GY = 190, 170   # separacion entre columnas y entre filas (los carriles de las flechas van ahi)

# ---------------------------------------------------------------------------------------------- lectura


def lineas(valor):
    v = re.sub(r"<br\s*/?>|</div>|<div>", "\n", valor or "")
    v = re.sub(r"<[^>]+>", "", v)
    v = html.unescape(v).replace("\xa0", " ")
    salida = []
    for l in v.split("\n"):
        l = re.sub(r"\s+", " ", l).strip()
        if not l:
            continue
        if salida and l[0] not in "+-#~":        # continuacion de la linea anterior (salto puesto a mano)
            salida[-1] = (salida[-1] + " " + l).replace("( ", "(")
        else:
            salida.append(l)
    return salida


raiz = ET.parse(ENTRADA).getroot()
celdas = list(raiz.iter("mxCell"))
por_id = {c.get("id"): c for c in celdas}

clases = {}         # nombre -> dict
id_a_nombre = {}
for c in celdas:
    if c.get("vertex") != "1" or c.get("parent") != "1":
        continue
    nombre = " ".join(lineas(c.get("value")))
    hijos = [k for k in celdas if k.get("parent") == c.get("id")]
    hijos.sort(key=lambda k: float(k.find("mxGeometry").get("y") or 0))
    textos = [k for k in hijos if (k.get("value") or "").strip()]
    sep = [k for k in hijos if (k.get("style") or "").startswith("line")]
    clases[nombre] = {
        "estilo": c.get("style"),
        "attrs": lineas(textos[0].get("value")) if len(textos) > 0 else [],
        "ops": lineas(textos[1].get("value")) if len(textos) > 1 else [],
        "estilo_texto": textos[0].get("style") if textos else None,
        "estilo_linea": sep[0].get("style") if sep else None,
        "interfaz": not hijos,
    }
    id_a_nombre[c.get("id")] = nombre

relaciones = []
for c in celdas:
    if c.get("edge") != "1":
        continue
    etiquetas = []
    for k in celdas:
        if k.get("parent") == c.get("id") and (k.get("value") or "").strip():
            x = float(k.find("mxGeometry").get("x") or 0)
            etiquetas.append((" ".join(lineas(k.get("value"))), "fin" if x > 0 else "inicio"))
    estilo = {}
    for par in (c.get("style") or "").split(";"):
        if "=" in par:
            k, v = par.split("=", 1)
            estilo[k] = v
    flecha = {k: estilo[k] for k in ("endArrow", "endFill", "startArrow", "startFill", "dashed", "endSize") if k in estilo}
    relaciones.append((id_a_nombre[c.get("source")], id_a_nombre[c.get("target")], flecha, etiquetas))

# ---------------------------------------------------------------------------------------------- tamanos


def alto_texto(ls, ancho):
    util = ancho - 10
    n = sum(max(1, math.ceil(len(l) * CHAR_CORTE / util)) for l in ls)
    return int(math.ceil(n * LINEA + PAD))


for nombre, k in clases.items():
    if k["interfaz"]:
        k["w"], k["h"] = 170, 50
        continue
    largas = [l for l in k["attrs"] + k["ops"] if not l.startswith(f"+ {nombre}(")] or [nombre]
    k["w"] = max(ANCHO_MIN, min(ANCHO_MAX, int(max(len(l) for l in largas) * CHAR) + 20))
    k["w"] = int(math.ceil(k["w"] / 10.0) * 10)
    k["ha"] = alto_texto(k["attrs"], k["w"]) if k["attrs"] else 20
    k["ho"] = alto_texto(k["ops"], k["w"]) if k["ops"] else 20
    k["h"] = CABECERA + k["ha"] + SEPARADOR + k["ho"]

# ---------------------------------------------------------------------------------------------- rejilla
#        c1             c2            c3           c4                  c5          c6
FILAS = [
    ["Notificacion", "Usuario", "Residente", "UsuarioAutorizado", "Vigilante", "Administrador"],
    [None, "Inmueble", "Auto", "ReservaZonaComun", None, "ReglaReserva"],
    ["Liquidacion", "Pago", "Paqueadero", "ZonaComun", None, "Rompimiento"],
    [None, "PasarelaPago", None, None, None, None],
]
INTERFAZ = "<<Interface>> NoAdmin"
faltan = set(clases) - {n for f in FILAS for n in f if n} - {INTERFAZ}
if faltan:
    raise SystemExit(f"clases sin lugar en la rejilla: {faltan}")

ncol = len(FILAS[0])
anchos = [max(clases[f[i]]["w"] for f in FILAS if f[i]) for i in range(ncol)]
altos = [max(clases[n]["h"] for n in f if n) for f in FILAS]
MARGEN_X, BANDA_SUP = 60, 230       # la banda superior lleva NoAdmin y los dos carriles que pasan por encima
xs, x = [], MARGEN_X
for a in anchos:
    xs.append(x)
    x += a + GX
ys, y = [], BANDA_SUP
for a in altos:
    ys.append(y)
    y += a + GY
ANCHO_PAG = int(x - GX + MARGEN_X)
ALTO_PAG = int(y - GY + 80)

for fi, f in enumerate(FILAS):
    for ci, n in enumerate(f):
        if n:
            k = clases[n]
            k["x"] = xs[ci] + (anchos[ci] - k["w"]) / 2
            k["y"] = ys[fi]
            k["col"], k["fila"] = ci, fi


def caja(n):
    k = clases[n]
    return k["x"], k["y"], k["w"], k["h"]


def cx(n):
    x, _, w, _ = caja(n)
    return x + w / 2


def borde(n, lado):
    x, y, w, h = caja(n)
    return {"arriba": y, "abajo": y + h, "izq": x, "der": x + w}[lado]


# NoAdmin: centrada sobre UsuarioAutorizado, en la banda superior
ua = clases["UsuarioAutorizado"]
ni = clases[INTERFAZ]
ni["x"] = cx("UsuarioAutorizado") - ni["w"] / 2
ni["y"] = BANDA_SUP - 110
T2 = ni["y"] - 45            # carril de Vigilante -> Usuario
T1 = ni["y"] - 90            # carril de Administrador -> Usuario
G01 = ys[1] - GY             # inicio del hueco entre fila 0 y fila 1
G12 = ys[2] - GY
G23 = ys[3] - GY


def gutter(ci):
    """x central del pasillo a la derecha de la columna ci."""
    return xs[ci] + anchos[ci] + GX / 2


# ---------------------------------------------------------------------------------------------- rutas
# Cada ruta: (lado de salida, x o y absoluta del punto de salida), (lado de llegada, x o y absoluta), [esquinas]
# Lados: arriba/abajo -> se da la x; izq/der -> se da la y.

def frac(n, lado, coord):
    x, y, w, h = caja(n)
    if lado in ("arriba", "abajo"):
        return (coord - x) / w, 0.0 if lado == "arriba" else 1.0
    return 0.0 if lado == "izq" else 1.0, (coord - y) / h


def px(n, f):
    x, _, w, _ = caja(n)
    return x + w * f


def py(n, f):
    _, y, _, h = caja(n)
    return y + h * f


def comun_y(a, b, f=0.5):
    """y dentro del tramo vertical que comparten las cajas a y b."""
    y1 = max(caja(a)[1], caja(b)[1])
    y2 = min(caja(a)[1] + caja(a)[3], caja(b)[1] + caja(b)[3])
    if y2 - y1 < 20:
        raise SystemExit(f"{a} y {b} no se solapan en vertical")
    return y1 + (y2 - y1) * f


rutas = {}


def ruta(o, d, salida, llegada, puntos=()):
    rutas[(o, d)] = (salida, llegada, list(puntos))


# fila 0: herencia y uso hacia Usuario, interfaz NoAdmin
y = comun_y("Notificacion", "Usuario", 0.45)
ruta("Notificacion", "Usuario", ("der", y), ("izq", y))
y = comun_y("Residente", "Usuario", 0.30)
ruta("Residente", "Usuario", ("izq", y), ("der", y))
y1, y2 = comun_y("Residente", "UsuarioAutorizado", 0.30), comun_y("Residente", "UsuarioAutorizado", 0.70)
ruta("UsuarioAutorizado", "Residente", ("izq", y1), ("der", y1))
ruta("Residente", "UsuarioAutorizado", ("der", y2), ("izq", y2))
ruta("UsuarioAutorizado", INTERFAZ, ("arriba", cx("UsuarioAutorizado")), ("abajo", cx("UsuarioAutorizado")))
ym = ni["y"] + ni["h"] / 2
xr = px("Residente", 0.85)
ruta("Residente", INTERFAZ, ("arriba", xr), ("izq", ym), [(xr, ym)])
xv = px("Vigilante", 0.25)
ruta("Vigilante", INTERFAZ, ("arriba", xv), ("der", ym), [(xv, ym)])
xv2, xu2 = px("Vigilante", 0.70), px("Usuario", 0.80)
ruta("Vigilante", "Usuario", ("arriba", xv2), ("arriba", xu2), [(xv2, T2), (xu2, T2)])
xa, xu1 = cx("Administrador"), px("Usuario", 0.55)
ruta("Administrador", "Usuario", ("arriba", xa), ("arriba", xu1), [(xa, T1), (xu1, T1)])

# columna 6: Administrador <- ReglaReserva <- Rompimiento
x6 = cx("Administrador")
ruta("ReglaReserva", "Administrador", ("arriba", x6), ("abajo", x6))
ruta("Rompimiento", "ReglaReserva", ("arriba", x6), ("abajo", x6))
y = comun_y("ZonaComun", "Rompimiento", 0.5)
ruta("ZonaComun", "Rompimiento", ("der", y), ("izq", y))

# entre fila 0 y fila 1
xc2 = cx("Usuario")
ruta("Inmueble", "Usuario", ("arriba", xc2), ("abajo", xc2))
la, lb = G01 + GY * 0.40, G01 + GY * 0.55
xr1, xi1 = px("Residente", 0.12), px("Inmueble", 0.85)
ruta("Residente", "Inmueble", ("abajo", xr1), ("arriba", xi1), [(xr1, la), (xi1, la)])
xc3 = cx("Residente")
ruta("Auto", "Residente", ("arriba", xc3), ("abajo", xc3))
xz, xr2 = px("ReservaZonaComun", 0.25), px("Residente", 0.85)
ruta("ReservaZonaComun", "Residente", ("arriba", xz), ("abajo", xr2), [(xz, lb), (xr2, lb)])
yi = py("Inmueble", 0.25)
xn = cx("Notificacion")
ruta("Inmueble", "Notificacion", ("izq", yi), ("abajo", xn), [(xn, yi)])

# fila 1 -> fila 2
xc4 = cx("ReservaZonaComun")
ruta("ReservaZonaComun", "ZonaComun", ("abajo", xc4), ("arriba", xc4))
ruta("Auto", "Paqueadero", ("abajo", xc3), ("arriba", xc3))
ruta("Pago", "Inmueble", ("arriba", px("Inmueble", 0.30)), ("abajo", px("Inmueble", 0.30)))
ruta("Inmueble", "Pago", ("abajo", px("Inmueble", 0.45)), ("arriba", px("Inmueble", 0.45)))
# carriles del hueco 1-2 (de arriba a abajo): Paqueadero, Liquidacion, ZonaComun (interior), ZonaComun (exterior)
ld, lc, lzb, lza = (G12 + GY * f for f in (0.30, 0.42, 0.58, 0.74))
xp, xi9 = px("Paqueadero", 0.15), px("Inmueble", 0.90)
ruta("Paqueadero", "Inmueble", ("arriba", xp), ("abajo", xi9), [(xp, ld), (xi9, ld)])
xl, xi0 = px("Liquidacion", 0.70), px("Inmueble", 0.12)
ruta("Liquidacion", "Inmueble", ("arriba", xl), ("abajo", xi0), [(xl, lc), (xi0, lc)])
# ZonaComun <-> Inmueble: salen por debajo de ZonaComun, pasan bajo Paqueadero y suben por el pasillo c2|c3
g = gutter(1)
base = max(borde("ZonaComun", "abajo"), borde("Paqueadero", "abajo"))
ya, yb = base + 70, base + 40                  # exterior mas abajo
xga, xgb = g - 20, g + 20                      # exterior mas a la izquierda
sa, sb = px("ZonaComun", 0.45), px("ZonaComun", 0.30)   # exterior sale mas a la derecha
ea, eb = px("Inmueble", 0.62), px("Inmueble", 0.76)     # exterior llega mas a la izquierda
ruta("ZonaComun", "Inmueble", ("abajo", sa), ("abajo", ea), [(sa, ya), (xga, ya), (xga, lza), (ea, lza)])
ruta("Inmueble", "ZonaComun", ("abajo", eb), ("abajo", sb), [(eb, lzb), (xgb, lzb), (xgb, yb), (sb, yb)])

# fila 2 -> fila 3
ruta("Pago", "PasarelaPago", ("abajo", xc2), ("arriba", xc2))

faltan = {(o, d) for o, d, _, _ in relaciones} - set(rutas)
if faltan:
    raise SystemExit(f"relaciones sin ruta: {faltan}")
if G23 + GY < ya + 40:
    raise SystemExit("los carriles bajo ZonaComun invaden la fila 3")

# ---------------------------------------------------------------------------------------------- XML


def esc(s):
    return html.escape(s, quote=True)


def valor_html(ls):
    return "<br>".join(html.escape(l, quote=False) for l in ls)


ID = "dcsh"
salida = []
n_id = [0]


def nid():
    n_id[0] += 1
    return f"{ID}-{n_id[0]}"


ids = {}
for nombre, k in clases.items():
    cid = nid()
    ids[nombre] = cid
    if k["interfaz"]:
        valor = "&lt;&lt;Interface&gt;&gt;<br><b>NoAdmin</b>"
        salida.append(
            f'<mxCell id="{cid}" value="{esc(valor)}" style="{esc(k["estilo"])}" vertex="1" parent="1">'
            f'<mxGeometry x="{k["x"]:g}" y="{k["y"]:g}" width="{k["w"]}" height="{k["h"]}" as="geometry"/></mxCell>')
        continue
    salida.append(
        f'<mxCell id="{cid}" value="{esc(nombre)}" style="{esc(k["estilo"])}" vertex="1" parent="1">'
        f'<mxGeometry x="{k["x"]:g}" y="{k["y"]:g}" width="{k["w"]}" height="{k["h"]}" as="geometry"/></mxCell>')
    y = CABECERA
    salida.append(
        f'<mxCell id="{nid()}" value="{esc(valor_html(k["attrs"]))}" style="{esc(k["estilo_texto"])}" vertex="1" '
        f'parent="{cid}"><mxGeometry y="{y}" width="{k["w"]}" height="{k["ha"]}" as="geometry"/></mxCell>')
    y += k["ha"]
    salida.append(
        f'<mxCell id="{nid()}" value="" style="{esc(k["estilo_linea"])}" vertex="1" parent="{cid}">'
        f'<mxGeometry y="{y}" width="{k["w"]}" height="{SEPARADOR}" as="geometry"/></mxCell>')
    y += SEPARADOR
    salida.append(
        f'<mxCell id="{nid()}" value="{esc(valor_html(k["ops"]))}" style="{esc(k["estilo_texto"])}" vertex="1" '
        f'parent="{cid}"><mxGeometry y="{y}" width="{k["w"]}" height="{k["ho"]}" as="geometry"/></mxCell>')

DESPLAZA = {"arriba": (14, -12), "abajo": (14, 12), "izq": (-16, -10), "der": (16, -10)}
for o, d, flecha, etiquetas in relaciones:
    (ls, cs), (ld_, cd), puntos = rutas[(o, d)]
    exx, exy = frac(o, ls, cs)
    enx, eny = frac(d, ld_, cd)
    estilo = ("edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;fontSize=12;"
              + "".join(f"{k}={v};" for k, v in flecha.items())
              + ("" if "endSize" in flecha else "endSize=12;")
              + f"exitX={exx:.4f};exitY={exy:.4f};exitDx=0;exitDy=0;exitPerimeter=0;"
              + f"entryX={enx:.4f};entryY={eny:.4f};entryDx=0;entryDy=0;entryPerimeter=0;")
    eid = nid()
    pts = "".join(f'<mxPoint x="{x:g}" y="{y:g}"/>' for x, y in puntos)
    salida.append(
        f'<mxCell id="{eid}" style="{estilo}" edge="1" parent="1" source="{ids[o]}" target="{ids[d]}">'
        f'<mxGeometry relative="1" as="geometry">' + (f'<Array as="points">{pts}</Array>' if pts else "")
        + '</mxGeometry></mxCell>')
    for texto, extremo in etiquetas:
        lado = ls if extremo == "inicio" else ld_
        dx, dy = DESPLAZA[lado]
        salida.append(
            f'<mxCell id="{nid()}" value="{esc(texto)}" style="edgeLabel;html=1;align=center;verticalAlign=middle;'
            f'resizable=0;points=[];fontSize=13;labelBackgroundColor=default;" vertex="1" connectable="0" '
            f'parent="{eid}"><mxGeometry x="{-1 if extremo == "inicio" else 1}" relative="1" as="geometry">'
            f'<mxPoint x="{dx}" y="{dy}" as="offset"/></mxGeometry></mxCell>')

xml = (
    '<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n'
    '  <diagram name="Pagina-1" id="V8wEmshVYFbSh1J9g9kn">\n'
    f'    <mxGraphModel dx="{ANCHO_PAG}" dy="{ALTO_PAG}" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
    f'arrows="1" fold="1" page="1" pageScale="1" pageWidth="{ANCHO_PAG}" pageHeight="{ALTO_PAG}" math="0" shadow="0">\n'
    '      <root>\n        <mxCell id="0"/>\n        <mxCell id="1" parent="0"/>\n'
    + "".join(f"        {s}\n" for s in salida)
    + "      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n")
ET.fromstring(xml.encode("utf-8"))
with open(SALIDA, "w", encoding="utf-8") as f:
    f.write(xml)
print(f"{len(clases)} clases, {len(relaciones)} relaciones, hoja {ANCHO_PAG} x {ALTO_PAG} -> {SALIDA}")
