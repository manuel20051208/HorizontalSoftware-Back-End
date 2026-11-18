"""Diagrama de despliegue UML de HorizontalSoftware: donde esta alojada la aplicacion completa
(navegador, front en Vercel, API en AWS EC2 detras de nginx, base de datos en Neon, Wompi y Gmail API).
Notacion segun OMG UML 2.5.1 (formal/17-12-05):
- 19.4.4: Node = cubo en perspectiva; Device con «device»; ExecutionEnvironment con «executionEnvironment».
- 19.3.4: Artifact = rectangulo con «artifact»; anidado dentro del nodo donde se despliega (19.4.4).
- 19.4.4: CommunicationPath = linea de Association (solida) entre nodos; se rotula con el nombre de la asociacion,
  no con estereotipos propios (que exigirian un Profile, clausula 12).
- 9.2.4.1: palabra clave entre comillas angulares, centrada encima del nombre; nombre centrado y en negrita.
- Anexo A: marco con encabezado [<kind>]<name>; para despliegue el kind es "dep".

Genera el XML de draw.io. Exportar despues con:
  python Diagramas/_fuente/exportar_drawio_svg.py Diagramas/Despliegue/despliegue.xml Diagramas/Despliegue/despliegue.drawio.svg

Uso: python gen_despliegue.py [salida.xml]     (por defecto Diagramas/Despliegue/despliegue.xml)
"""
import os
import sys
from xml.sax.saxutils import escape

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "..", "Despliegue", "despliegue.xml")
PAGE_W, PAGE_H = 1810, 800

NODO = ("shape=cube;size=12;direction=south;html=1;whiteSpace=wrap;boundedLbl=1;verticalAlign=top;align=center;"
        "spacingTop=4;fontSize=12;strokeColor=#36393d;")
ENTORNO = NODO.replace("size=12", "size=8")
ARTEFACTO = ("rounded=0;html=1;whiteSpace=wrap;fontSize=11;align=center;verticalAlign=middle;"
             "strokeColor=#36393d;fillColor=#ffffff;")
ENLACE = ("edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=none;strokeColor=#000000;"
          "fontSize=11;labelBackgroundColor=#ffffff;")

celdas = []
nodos = set()   # cajas 3D (direction=south): draw.io gira sus puntos de conexion


def girar(id_, punto):
    """Con direction=south draw.io lleva el punto (x, y) a (1 - y, x); se pasa el inverso para que quede donde se pide."""
    x, y = punto
    return (round(y, 4), round(1 - x, 4)) if id_ in nodos else (x, y)


def caja(id_, x, y, w, h, texto, estilo):
    celdas.append(f'<mxCell id="{id_}" value="{escape(texto, {chr(34): "&quot;"})}" style="{estilo}" vertex="1" '
                  f'parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')


def nodo(id_, x, y, w, h, palabra, nombre, relleno="#dae8fc", estilo=NODO):
    """Nodo UML: caja 3D con su palabra clave (si tiene) y su nombre."""
    texto = (f"&laquo;{palabra}&raquo;<br>" if palabra else "") + f"<b>{nombre}</b>"
    nodos.add(id_)
    caja(id_, x, y, w, h, texto, estilo + f"fillColor={relleno};")


def artefacto(id_, x, y, w, h, nombre):
    caja(id_, x, y, w, h, f"&laquo;artifact&raquo;<br><b>{nombre}</b>", ARTEFACTO)


def enlace(id_, origen, destino, texto, salida, entrada, puntos=(), extra="", rotulo_x=None):
    sx, sy = girar(origen, salida)
    ex, ey = girar(destino, entrada)
    pts = "".join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in puntos)
    rx = f' x="{rotulo_x}"' if rotulo_x is not None else ""   # posicion del rotulo: -1 origen, 0 centro, 1 destino
    geo = (f'<mxGeometry relative="1"{rx} as="geometry"><Array as="points">{pts}</Array></mxGeometry>' if pts
           else f'<mxGeometry relative="1"{rx} as="geometry"/>')
    estilo = (ENLACE + f"exitX={sx};exitY={sy};exitDx=0;exitDy=0;entryX={ex};entryY={ey};entryDx=0;entryDy=0;"
              + extra)
    celdas.append(f'<mxCell id="{id_}" value="{escape(texto, {chr(34): "&quot;"})}" style="{estilo}" edge="1" '
                  f'parent="1" source="{origen}" target="{destino}">{geo}</mxCell>')


# ---------------------------------------------------------------- marco (Anexo A): encabezado "dep <nombre>"
caja("marco", 20, 90, 1770, 690, "<b>dep</b> HorizontalSoftware",
     "shape=umlFrame;whiteSpace=wrap;html=1;width=210;height=30;boundedLbl=1;verticalAlign=middle;align=left;"
     "spacingLeft=8;fontSize=13;fillColor=none;strokeColor=#36393d;")

# ---------------------------------------------------------------- nodos
nodo("cli", 40, 210, 220, 180, "device", "Dispositivo del usuario", "#f5f5f5")
nodo("nav", 60, 270, 180, 100, "executionEnvironment", "Navegador web", "#ffffff", ENTORNO)

nodo("ver", 380, 180, 240, 240, "", "Vercel", "#e1d5e7")
nodo("node", 400, 240, 200, 160, "executionEnvironment", "Node.js", "#f8f0fb", ENTORNO)
artefacto("front", 410, 310, 180, 60, "software-residencial")

nodo("ec2", 800, 150, 540, 390, "device", "AWS EC2 (Ubuntu)", "#ffe6cc")
nodo("nginx", 830, 270, 150, 100, "executionEnvironment", "Nginx", "#fff5eb", ENTORNO)
nodo("docker", 1050, 220, 270, 300, "executionEnvironment", "Docker", "#fff5eb", ENTORNO)
nodo("cont", 1070, 270, 230, 200, "executionEnvironment", "Contenedor horizontal-api", "#ffffff", ENTORNO)
artefacto("jar", 1095, 360, 180, 60, "demo-0.0.1-SNAPSHOT.jar")

nodo("neon", 1500, 150, 260, 150, "", "Neon", "#d5e8d4")
nodo("pg", 1520, 200, 220, 80, "executionEnvironment", "PostgreSQL", "#ffffff", ENTORNO)

nodo("wompi", 1500, 560, 260, 110, "", "Wompi", "#f8cecc")
nodo("gmail", 1500, 375, 260, 90, "", "Gmail API", "#f5f5f5")

# ---------------------------------------------------------------- caminos de comunicacion
enlace("c1", "nav", "node", "HTTPS :443", (1, 0.5), (0, 0.5))
enlace("c2", "node", "nginx", "HTTPS :443<br>horizontal-proyect.duckdns.org", (1, 0.5), (0, 0.5))
enlace("c3", "nginx", "cont", "HTTP<br>:8080", (1, 0.5), (0, 0.25), rotulo_x=-0.45)   # rotulo del lado de Nginx, fuera del borde de Docker
enlace("c4", "cont", "pg", "JDBC/TLS :5432", (1, 0.25), (0, 0.5), ((1410, 320), (1410, 240)))
enlace("c5", "cont", "gmail", "HTTPS :443", (1, 0.75), (0, 0.5))
enlace("c6", "cont", "wompi", "HTTPS :443", (0.5, 1), (0, 0.3636), ((1185, 600),))
enlace("c7", "wompi", "nginx", "HTTPS :443", (0, 0.7273), (0.5, 1), ((905, 640),))
enlace("c8", "nav", "wompi", "HTTPS :443", (0.5, 1), (0.5, 1), ((150, 730), (1630, 730)))

os.makedirs(os.path.dirname(os.path.abspath(SALIDA)), exist_ok=True)
with open(SALIDA, "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n'
            '  <diagram name="Despliegue" id="despliegue">\n'
            f'    <mxGraphModel grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" '
            f'page="1" pageScale="1" pageWidth="{PAGE_W}" pageHeight="{PAGE_H}" background="#ffffff" math="0" '
            'shadow="0">\n      <root>\n        <mxCell id="0"/>\n        <mxCell id="1" parent="0"/>\n'
            + "".join(f"        {c}\n" for c in celdas)
            + "      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n")
print(SALIDA)
