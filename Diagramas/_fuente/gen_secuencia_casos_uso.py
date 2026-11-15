#!/usr/bin/env python3
"""
Genera un diagrama de secuencia por caso de uso:
  - Diagramas/_fuente/secuencia-casos-de-uso.xml  (draw.io / diagrams.net, una pagina por diagrama; es la fuente)
  - Diagramas/Secuencia/<caso-de-uso>.svg          (un SVG por diagrama, dibujado desde ese mismo XML)

Regla: los participantes son clases del diagrama de clases y los mensajes son SUS operaciones, con los
nombres y parametros tal como estan en el diagrama de clases. Los casos de uso son los del diagrama de
casos de uso (Diagramas/UML).

Uso:  python Diagramas/_fuente/gen_secuencia_casos_uso.py                  (solo los SVG)
      python Diagramas/_fuente/gen_secuencia_casos_uso.py --regenerar-xml  (tambien el XML; descarta los
                                                                            retoques hechos a mano en draw.io)
"""
import html
import math
import os
import re
import unicodedata
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from conectar_secuencia import conectar_xml  # noqa: E402

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SALIDA = os.path.join(RAIZ, "Diagramas", "_fuente", "secuencia-casos-de-uso.xml")
CARPETA_SVG = os.path.join(RAIZ, "Diagramas", "Secuencia")

ESPACIO = 270          # distancia entre lifelines
ANCHO_CAJA = 150       # ancho del encabezado de cada lifeline
TOP = 60               # y del encabezado
Y0 = 170               # y de la primera interaccion
CHAR = 6.4             # ancho aproximado de un caracter (px) a 11 px

AZUL = "#cce5ff"
BORDE = "#36393d"


def esc(s):
    """Escapa una cadena para ponerla en un atributo XML."""
    return html.escape(s, quote=True)


def etiqueta_html(lineas):
    """Texto de una etiqueta html=1 de draw.io: una linea (str) o varias (lista), separadas con <br>.

    draw.io trata el valor como HTML, asi que el texto se escapa PRIMERO como HTML y luego, con esc(), para el
    XML. Sin este paso 'List<Usuario>' llega a draw.io como la etiqueta <Usuario> y solo se dibuja 'List'.
    """
    if isinstance(lineas, str):
        lineas = [lineas]
    return "<br>".join(html.escape(linea, quote=False) for linea in lineas)


def envolver(texto, ancho_px):
    """Parte el texto en lineas (separador <br>) cortando en ', ' o espacios."""
    maxc = max(12, int(ancho_px / CHAR))
    if len(texto) <= maxc:
        return [texto]
    palabras = texto.replace(", ", ",\u0001").split("\u0001")
    lineas, actual = [], ""
    for p in palabras:
        p = p.replace("\u0001", " ")
        if actual and len(actual) + len(p) + (0 if actual.endswith(",") else 1) > maxc:
            lineas.append(actual.rstrip())
            actual = p
        else:
            actual = (actual + (" " if actual and not actual.endswith(",") else (" " if actual else ""))) + p
    if actual:
        lineas.append(actual.strip())
    # una palabra sin comas ni espacios (poco probable) se deja tal cual
    return lineas


class Diagrama:
    def __init__(self, nombre, titulo, participantes, nota=None):
        """participantes: lista de (id, tipo, etiqueta); tipo = 'actor' | 'obj'."""
        self.nombre = nombre
        self.titulo = titulo
        self.nota = nota
        self.parts = {}
        self.orden = []
        for i, (pid, tipo, etiqueta) in enumerate(participantes):
            cx = 110 + i * ESPACIO
            self.parts[pid] = dict(tipo=tipo, etiqueta=etiqueta, cx=cx)
            self.orden.append(pid)
        self.y = Y0
        self.celdas_frames = []
        self.celdas_act = []
        self.celdas_msg = []
        self.celdas_txt = []
        self.pila_frames = []
        self.activaciones = {pid: [] for pid in self.orden}
        self._n = 0
        self.max_x = 110 + (len(participantes) - 1) * ESPACIO + 120

    # ------------------------------------------------------------ utilidades
    def _id(self, pref):
        self._n += 1
        return f"{pref}{self._n}"

    def cx(self, pid):
        return self.parts[pid]["cx"]

    def _tocar(self, x1, x2):
        self.max_x = max(self.max_x, x2)
        if self.pila_frames:
            f = self.pila_frames[-1]
            f["min"] = min(f["min"], x1)
            f["max"] = max(f["max"], x2)

    # ------------------------------------------------------------ mensajes
    def _mensaje(self, a, b, etiqueta, retorno):
        xa, xb = self.cx(a), self.cx(b)
        ancho = abs(xb - xa) - 30
        lineas = envolver(etiqueta, ancho)
        h = 15 * len(lineas)
        y = self.y + h + 8
        # la punta toca el borde de la barra de activacion (5 px a cada lado del eje); el actor no tiene barra,
        # asi que su punta va sobre su linea
        da = 0 if self.parts[a]["tipo"] == "actor" else 5
        db = 0 if self.parts[b]["tipo"] == "actor" else 5
        if xb > xa:
            sx, tx = xa + da, xb - db
        else:
            sx, tx = xa - da, xb + db
        estilo = ("html=1;verticalAlign=bottom;rounded=0;fontSize=11;strokeColor=#000000;"
                  + ("dashed=1;endArrow=open;endFill=0;endSize=8;" if retorno
                     else "endArrow=block;endFill=1;endSize=8;"))
        self.celdas_msg.append(
            f'<mxCell id="{self._id("m")}" value="{esc(etiqueta_html(lineas))}" style="{estilo}" '
            f'edge="1" parent="1"><mxGeometry relative="1" as="geometry">'
            f'<mxPoint x="{sx}" y="{y}" as="sourcePoint"/><mxPoint x="{tx}" y="{y}" as="targetPoint"/>'
            f'</mxGeometry></mxCell>')
        self._tocar(min(sx, tx) - 25, max(sx, tx) + 25)
        self.y = y + 14
        return y

    def llamar(self, a, b, etiqueta):
        """Mensaje sincrono; deja abierta la activacion de b hasta su retorno."""
        y = self._mensaje(a, b, etiqueta, False)
        self.activaciones[b].append(y)

    def mensaje(self, a, b, etiqueta):
        """Mensaje sin activacion (interaccion con un sistema externo)."""
        self._mensaje(a, b, etiqueta, False)

    def retornar(self, b, a, etiqueta, cerrar=True):
        """Retorno (linea punteada) de b hacia a; cierra la activacion de b si cerrar=True."""
        y = self._mensaje(b, a, etiqueta, True)
        if cerrar and self.activaciones[b]:
            y0 = self.activaciones[b].pop()
            self._activacion(b, y0 - 6, y + 6)

    def llamar_y_retornar(self, a, b, etiqueta, retorno):
        """Llamada corta de a hacia b con su retorno inmediato."""
        self.llamar(a, b, etiqueta)
        self.retornar(b, a, retorno)

    def propio(self, a, etiqueta, retorno=None):
        """Mensaje de un objeto a si mismo (con su retorno opcional en el texto)."""
        cx = self.cx(a)
        texto = etiqueta if retorno is None else f"{etiqueta} : {retorno}"
        ancho_txt = ESPACIO - 85
        lineas = envolver(texto, ancho_txt)
        alto = max(24, 15 * len(lineas) + 4)
        y = self.y + 12
        x1 = cx + 10
        self.celdas_msg.append(
            f'<mxCell id="{self._id("s")}" value="" style="html=1;rounded=0;endArrow=block;endFill=1;'
            f'endSize=8;strokeColor=#000000;" edge="1" parent="1"><mxGeometry relative="1" as="geometry">'
            f'<mxPoint x="{x1}" y="{y}" as="sourcePoint"/><mxPoint x="{x1}" y="{y + alto}" as="targetPoint"/>'
            f'<Array as="points"><mxPoint x="{x1 + 38}" y="{y}"/><mxPoint x="{x1 + 38}" y="{y + alto}"/>'
            f'</Array></mxGeometry></mxCell>')
        self._texto(x1 + 44, y - 2, ancho_txt, alto + 4, etiqueta_html(lineas), "left")
        self._activacion(a, y - 4, y + alto + 4, desplazo=5)
        self._tocar(cx - 25, x1 + 44 + min(ancho_txt, int(max(len(l) for l in lineas) * CHAR)) + 15)
        self.y = y + alto + 10

    def _activacion(self, pid, y1, y2, desplazo=0):
        x = self.cx(pid) - 5 + desplazo
        self.celdas_act.append(
            f'<mxCell id="{self._id("a")}" value="" style="html=1;points=[];perimeter=orthogonalPerimeter;'
            f'fillColor=#ffffff;strokeColor=#000000;" vertex="1" parent="1">'
            f'<mxGeometry x="{x}" y="{y1}" width="10" height="{max(14, y2 - y1)}" as="geometry"/></mxCell>')

    def _texto(self, x, y, w, h, valor, align="left", estilo_extra=""):
        """valor ya es HTML (ver etiqueta_html)."""
        self.celdas_txt.append(
            f'<mxCell id="{self._id("t")}" value="{esc(valor)}" style="text;html=1;align={align};'
            f'verticalAlign=top;whiteSpace=wrap;overflow=hidden;fontSize=11;{estilo_extra}" '
            f'vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')

    # ------------------------------------------------------------ fragmentos
    def abrir(self, tipo, condicion):
        self.y += 14
        self.pila_frames.append(dict(tipo=tipo, top=self.y, min=10 ** 6, max=0, ramas=[], cond=condicion))
        self.y += 34

    def sino(self, condicion):
        f = self.pila_frames[-1]
        self.y += 6
        f["ramas"].append((self.y, condicion))
        self.y += 34

    def cerrar(self):
        f = self.pila_frames.pop()
        self.y += 8
        x1, x2 = f["min"] - 15, f["max"] + 15
        if x1 > x2:
            x1, x2 = 10, 200
        x1 = max(10, x1)
        top, bottom = f["top"], self.y
        ancho = x2 - x1
        self.celdas_frames.append(
            f'<mxCell id="{self._id("f")}" value="{esc(etiqueta_html(f["tipo"]))}" style="shape=umlFrame;whiteSpace=wrap;'
            f'html=1;pointerEvents=0;fillColor=none;strokeColor=#36393d;fontStyle=1;fontSize=11;width=55;'
            f'height=22;" vertex="1" parent="1"><mxGeometry x="{x1}" y="{top}" width="{ancho}" '
            f'height="{bottom - top}" as="geometry"/></mxCell>')
        self.celdas_txt.append(
            f'<mxCell id="{self._id("g")}" value="{esc(etiqueta_html("[" + f["cond"] + "]"))}" style="text;html=1;align=left;'
            f'verticalAlign=top;whiteSpace=wrap;fontSize=11;fontStyle=2;" vertex="1" parent="1">'
            f'<mxGeometry x="{x1 + 62}" y="{top + 3}" width="{max(120, ancho - 70)}" height="22" '
            f'as="geometry"/></mxCell>')
        for ry, cond in f["ramas"]:
            self.celdas_frames.append(
                f'<mxCell id="{self._id("d")}" value="" style="endArrow=none;dashed=1;html=1;strokeColor=#36393d;" '
                f'edge="1" parent="1"><mxGeometry relative="1" as="geometry">'
                f'<mxPoint x="{x1}" y="{ry}" as="sourcePoint"/><mxPoint x="{x2}" y="{ry}" as="targetPoint"/>'
                f'</mxGeometry></mxCell>')
            self.celdas_txt.append(
                f'<mxCell id="{self._id("g")}" value="{esc(etiqueta_html("[" + cond + "]"))}" style="text;html=1;align=left;'
                f'verticalAlign=top;whiteSpace=wrap;fontSize=11;fontStyle=2;" vertex="1" parent="1">'
                f'<mxGeometry x="{x1 + 8}" y="{ry + 3}" width="{max(120, ancho - 20)}" height="22" '
                f'as="geometry"/></mxCell>')
        self.y += 14
        if self.pila_frames:
            p = self.pila_frames[-1]
            p["min"] = min(p["min"], x1)
            p["max"] = max(p["max"], x2)
        self.max_x = max(self.max_x, x2 + 20)

    # ------------------------------------------------------------ salida XML
    def xml(self):
        """XML final para draw.io: mensajes conectados a barras/lineas de vida y fragmentos al frente."""
        texto, avisos, _ = conectar_xml(self._xml_crudo())
        for a in avisos:
            print("AVISO", a)
        return "  " + texto.strip() + "\n"

    def _xml_crudo(self):
        """Celdas con coordenadas absolutas (lo que se dibuja en el SVG)."""
        fin = self.y + 30
        celdas = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
        # titulo
        celdas.append(
            f'<mxCell id="titulo" value="{esc("<b>" + etiqueta_html(self.titulo) + "</b>")}" style="text;html=1;align=left;'
            f'verticalAlign=top;whiteSpace=wrap;fontSize=15;" vertex="1" parent="1">'
            f'<mxGeometry x="20" y="12" width="{max(500, self.max_x - 40)}" height="26" as="geometry"/></mxCell>')
        if self.nota:
            celdas.append(
                f'<mxCell id="nota" value="{esc(etiqueta_html(self.nota))}" style="text;html=1;align=left;verticalAlign=top;'
                f'whiteSpace=wrap;fontSize=11;fontColor=#555555;" vertex="1" parent="1">'
                f'<mxGeometry x="20" y="34" width="{max(500, self.max_x - 40)}" height="18" as="geometry"/></mxCell>')
        # lifelines
        for pid in self.orden:
            p = self.parts[pid]
            cx = p["cx"]
            if p["tipo"] == "actor":
                celdas.append(
                    f'<mxCell id="L-{pid}" value="{esc(etiqueta_html(p["etiqueta"]))}" style="shape=umlActor;'
                    f'verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fontStyle=1;'
                    f'fontSize=11;whiteSpace=wrap;fillColor=#ffcc99;strokeColor=#36393d;" vertex="1" parent="1">'
                    f'<mxGeometry x="{cx - 14}" y="{TOP}" width="28" height="56" as="geometry"/></mxCell>')
                celdas.append(
                    f'<mxCell id="V-{pid}" value="" style="endArrow=none;dashed=1;html=1;strokeColor=#666666;" '
                    f'edge="1" parent="1"><mxGeometry relative="1" as="geometry">'
                    f'<mxPoint x="{cx}" y="{Y0 - 14}" as="sourcePoint"/><mxPoint x="{cx}" y="{fin}" as="targetPoint"/>'
                    f'</mxGeometry></mxCell>')
            else:
                celdas.append(
                    f'<mxCell id="L-{pid}" value="{esc(etiqueta_html(p["etiqueta"]))}" style="shape=umlLifeline;'
                    f'perimeter=lifelinePerimeter;whiteSpace=wrap;html=1;container=0;dropTarget=0;'
                    f'collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;'
                    f'fillColor={AZUL};strokeColor={BORDE};fontStyle=1;fontSize=12;" vertex="1" parent="1">'
                    f'<mxGeometry x="{cx - ANCHO_CAJA // 2}" y="{TOP}" width="{ANCHO_CAJA}" '
                    f'height="{fin - TOP}" as="geometry"/></mxCell>')
        celdas += self.celdas_act + self.celdas_msg + self.celdas_txt + self.celdas_frames   # fragmentos al frente
        ancho = self.max_x + 40
        cuerpo = "\n        ".join(celdas)
        return (f'  <diagram name="{esc(self.nombre)}" id="{esc(self.nombre.lower().replace(" ", "-"))}">\n'
                f'    <mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
                f'arrows="1" fold="1" page="1" pageScale="1" pageWidth="{ancho}" pageHeight="{fin + 60}" '
                f'math="0" shadow="0">\n      <root>\n        {cuerpo}\n      </root>\n    </mxGraphModel>\n'
                f'  </diagram>\n')


# =========================================================================================================
#  CASOS DE USO
# =========================================================================================================
SIS = ("act", "actor", "Sistema")
ADMIN = ("act", "actor", "Administrador")
RESI = ("act", "actor", "Residente")
VIGI = ("act", "actor", "Vigilante")
TRES = ("act", "actor", "Residente, Vigilante o Administrador")


def o(pid, clase):
    return (pid, "obj", f":{clase}")


diagramas = []


def nuevo(nombre, participantes, nota=None):
    d = Diagrama(nombre, f"Diagrama de secuencia: {nombre}", participantes, nota)
    diagramas.append(d)
    return d


def enviar(d, de, llamada, destinatarios_del_inmueble=True, apoyo=None):
    """de -> Notificacion.<llamada>: toma los destinatarios del inmueble (residentesActivos) y se envia."""
    d.llamar(de, "not", llamada)
    if destinatarios_del_inmueble:
        d.llamar_y_retornar("not", "inm", "residentesActivos()", "List<Usuario>")
    if apoyo:
        d.propio("not", *apoyo)
    d.propio("not", "enviarNotificacion()", "Boolean")
    d.retornar("not", de, "")


def marcar_leida(d):
    """actor -> Usuario.marcarNotificacionLeida(inmueble, id)."""
    d.llamar("act", "usr", "marcarNotificacionLeida(inmueble, id)")
    d.propio("usr", "esEste(id)", "boolean")
    d.propio("usr", "perteneceA(inmueble)", "boolean")
    d.retornar("usr", "act", "")


# Regla de todos los diagramas: cada flecha es una llamada que el codigo del modelo hace de verdad
# (src/main/java/unisimon/demo/model), con el nombre y los parametros del diagrama de clases, y cada retorno es el
# tipo de retorno de esa operacion (vacio si es void). Lo que orquesta la aplicacion sale del actor.

# ---------------------------------------------------------------- Cuenta y sesion
d = nuevo("Iniciar sesión", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "iniciarSesion(correo, contrasena)")
d.propio("usr", "isActive()", "Boolean")
d.propio("usr", "credencialesCorrectas(correo, contrasena)", "boolean")
d.abrir("alt", "cuenta activa y credenciales correctas")
d.retornar("usr", "act", "Usuario", cerrar=False)
d.sino("cuenta inactiva o credenciales incorrectas")
d.retornar("usr", "act", "null")
d.cerrar()

d = nuevo("Activar cuenta creada", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "activarCuenta(correo, contrasena)")
d.propio("usr", "isActive()", "Boolean")
d.propio("usr", "credencialesCorrectas(correo, contrasena)", "boolean")
d.abrir("alt", "credenciales correctas y cuenta aún inactiva")
d.retornar("usr", "act", "true", cerrar=False)
d.sino("credenciales incorrectas o cuenta ya activa")
d.retornar("usr", "act", "false")
d.cerrar()

d = nuevo("Recuperar contraseña", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "recuperarContrasena()")
d.propio("usr", "cerrarSesion()")
d.retornar("usr", "act", "")

d = nuevo("Gestionar cuentas de usuario", [ADMIN, o("adm", "Administrador"), o("usr", "Usuario")])
d.llamar("act", "adm", "gestionarCuentaUsuario()")
d.propio("adm", "exigirVigente()")
d.retornar("adm", "act", "")
d.abrir("opt", "activar o desactivar una cuenta existente")
d.llamar("act", "adm", "modificarActive(identificacion, idToken)")
d.propio("adm", "exigirAutoridad(adminId)")
d.llamar_y_retornar("adm", "usr", "isActive()", "Boolean")
d.abrir("opt", "la cuenta queda inactiva")
d.llamar_y_retornar("adm", "usr", "cerrarSesion()", "")
d.cerrar()
d.retornar("adm", "act", "NoAdmin")
d.cerrar()

# ---------------------------------------------------------------- Configuracion
d = nuevo("Configurar multas", [ADMIN, o("adm", "Administrador"), o("liq", "Liquidacion")])
d.llamar("act", "adm", "configurarMultas(inmueble, adminId)")
d.propio("adm", "exigirAutoridad(adminId)")
d.propio("adm", "liquidacionVigente(inmueble)", "Liquidacion")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.retornar("adm", "act", "")

d = nuevo("Configurar tarifas", [ADMIN, o("adm", "Administrador"), o("liq", "Liquidacion")])
d.llamar("act", "adm", "configurarTarifas(inmueble, adminId)")
d.propio("adm", "exigirAutoridad(adminId)")
d.propio("adm", "liquidacionVigente(inmueble)", "Liquidacion")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.retornar("adm", "act", "")

d = nuevo("Configurar zonas comunes", [ADMIN, o("adm", "Administrador"), o("zon", "ZonaComun")])
d.llamar("act", "adm", "ConfigurarZonasComunes(inmueble, adminId)")
d.propio("adm", "exigirAutoridad(adminId)")
d.abrir("loop", "por cada zona común del inmueble")
d.llamar_y_retornar("adm", "zon", "cambiarEstado()", "")
d.cerrar()
d.retornar("adm", "act", "")

d = nuevo("Configurar reglas de reserva",
          [ADMIN, o("adm", "Administrador"), o("reg", "ReglaReserva"), o("rom", "Rompimiento")])
d.abrir("opt", "cambiar los valores de la regla")
d.llamar_y_retornar("act", "reg", "actualizarReglas(nuevaAnticipacion, nuevaDuracion)", "")
d.cerrar()
d.llamar("act", "adm", "ConfigurarNuevaReglaReserva(inmueble, adminId)")
d.propio("adm", "exigirAutoridad(adminId)")
d.abrir("loop", "por cada regla asignada a las zonas comunes del inmueble")
d.propio("adm", "esEste(id)", "boolean")
d.abrir("opt", "la regla es de este administrador")
d.llamar_y_retornar("adm", "rom", "aplicarRegla()", "")
d.cerrar()
d.cerrar()
d.retornar("adm", "act", "")
d.abrir("opt", "retirar la regla de una zona")
d.llamar_y_retornar("act", "rom", "inactivarRegla()", "")
d.cerrar()

# ---------------------------------------------------------------- Pagos y liquidaciones
d = nuevo("Consultar estado de cuenta e historial de pagos",
          [RESI, o("res", "Residente"), o("liq", "Liquidacion"), o("inm", "Inmueble")])
d.llamar("act", "res", "consultarEstadoCuenta(inmueble, id)")
d.propio("res", "exigirPropio(id)")
d.propio("res", "exigirInmueble(otro)")
d.llamar_y_retornar("res", "liq", "calcularTotal()", "BigDecimal")
d.retornar("res", "act", "Liquidacion")
d.abrir("opt", "sin saldo pendiente y con liquidación")
d.llamar_y_retornar("act", "inm", "pagoAlDiaAdministracion(liquidacion)", "boolean")
d.cerrar()
d.llamar("act", "res", "consultarHistorialPago(id)")
d.propio("res", "exigirPropio(id)")
d.retornar("res", "act", "List<Pago>")

d = nuevo("Realizar pagos",
          [RESI, o("pag", "Pago"), o("pas", "PasarelaPago"), o("liq", "Liquidacion"), o("inm", "Inmueble"),
           o("not", "Notificacion")])
d.llamar_y_retornar("act", "pag", "registrarPago(inmueble)", "Pago")
d.llamar("act", "pas", "iniciarTransaccion(pago)")
d.propio("pas", "iniciarTransaccion(pago, secretoIntegridad, redirectUrl)", "String")
d.propio("pas", "enc(v)", "String")
d.propio("pas", "firmaIntegridad(referencia, centavos, secretoIntegridad)", "String")
d.retornar("pas", "act", "String")
d.llamar("act", "pas", "confirmarTransaccion(referencia)")
d.propio("pas", "buscarPago(referencia)", "Pago")
d.llamar_y_retornar("pas", "pag", "estaAprobado(pago)", "Boolean")
d.retornar("pas", "act", "Boolean")
d.abrir("opt", "transacción aprobada")
d.llamar("act", "pag", "actualizarEstado(pago)")
d.propio("pag", "estaAprobado(pago)", "Boolean")
d.llamar_y_retornar("pag", "liq", "marcarAlDia()", "")
d.retornar("pag", "act", "Liquidacion")
d.llamar("act", "pas", "notificarResultado(pago)")
d.llamar_y_retornar("pas", "inm", "residentesActivos()", "List<Usuario>")
d.llamar("pas", "not", "notificarActualizacionDePago(inmueble, usuarios)")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "pas", "")
d.retornar("pas", "act", "")
d.llamar_y_retornar("act", "pag", "generarComprobante()", "String")
d.cerrar()
d.llamar("act", "pas", "consultarEstado(referencia)")
d.propio("pas", "buscarPago(referencia)", "Pago")
d.retornar("pas", "act", "String")

d = nuevo("Conciliar pagos de administración",
          [ADMIN, o("adm", "Administrador"), o("pag", "Pago"), o("liq", "Liquidacion")])
d.llamar("act", "adm", "conciliarPagosAdministracion(inmueble, adminId)")
d.propio("adm", "exigirAutoridad(adminId)")
d.propio("adm", "liquidacionVigente(inmueble)", "Liquidacion")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.llamar_y_retornar("adm", "pag", "validarMonto(liquidacion)", "Boolean")
d.abrir("alt", "monto igual al total de la liquidación")
d.llamar("adm", "pag", "actualizarEstado(pago)")
d.propio("pag", "estaAprobado(pago)", "Boolean")
d.llamar_y_retornar("pag", "liq", "marcarAlDia()", "")
d.retornar("pag", "adm", "Liquidacion")
d.retornar("adm", "act", "Pago", cerrar=False)
d.sino("monto distinto al total de la liquidación")
d.llamar_y_retornar("adm", "pag", "anularPago()", "")
d.retornar("adm", "act", "")
d.cerrar()

d = nuevo("Generar liquidación mensual", [SIS, o("liq", "Liquidacion")])
d.abrir("loop", "por cada inmueble activo")
d.llamar("act", "liq", "generarLiquidacion(inmueble)")
d.propio("liq", "calcularTotal()", "BigDecimal")
d.propio("liq", "nz(v)", "BigDecimal")
d.retornar("liq", "act", "Liquidacion")
d.cerrar()

# ---------------------------------------------------------------- Comunicados
d = nuevo("Consultar comunicado oficial", [RESI, o("res", "Residente"), o("usr", "Usuario")])
d.llamar("act", "res", "consultarNotificacion(inmueble, id)")
d.propio("res", "exigirPropio(id)")
d.propio("res", "exigirInmueble(otro)")
d.retornar("res", "act", "List<Notificacion>")
marcar_leida(d)

d = nuevo("Publicar comunicado oficial", [ADMIN, o("inm", "Inmueble"), o("not", "Notificacion")])
d.llamar_y_retornar("act", "inm", "mostrarUsuarios(List<Usuario>)", "List<String>")
d.llamar_y_retornar("act", "not", "Notificacion(id, usuarios, mensaje, tipoNotificacion, fechaEnvio)", "")
d.llamar("act", "not", "enviarNotificacion()")
d.abrir("alt", "hay destinatarios y mensaje")
d.retornar("not", "act", "true", cerrar=False)
d.sino("sin destinatarios o sin mensaje")
d.retornar("not", "act", "false")
d.cerrar()

d = nuevo("Consultar confirmación de lectura", [ADMIN, o("adm", "Administrador")])
d.llamar("act", "adm", "consultarConfirmacionLectura()")
d.propio("adm", "exigirVigente()")
d.retornar("adm", "act", "")

# ---------------------------------------------------------------- Zonas comunes y reservas
d = nuevo("Consultar calendario de disponibilidad",
          [("act", "actor", "Residente o Vigilante"), o("res", "Residente"), o("vig", "Vigilante"),
           o("rez", "ReservaZonaComun"), o("zon", "ZonaComun")])
d.abrir("alt", "actor = Residente")
d.llamar("act", "res", "verReservas(id)")
d.propio("res", "exigirPropio(id)")
d.retornar("res", "act", "List<ReservaZonaComun>")
d.sino("actor = Vigilante")
d.llamar_y_retornar("act", "vig", "consultarReservas()", "List<ZonaComun>")
d.cerrar()
d.abrir("loop", "por cada zona común")
d.llamar("act", "rez", "verificarDisponibilidad(hora)")
d.propio("rez", "validarHorario(hora)", "Boolean")
d.llamar_y_retornar("rez", "zon", "horarioIncluye(hora)", "boolean")
d.retornar("rez", "act", "Boolean")
d.cerrar()

d = nuevo("Reservar zona común",
          [RESI, o("res", "Residente"), o("rez", "ReservaZonaComun"), o("zon", "ZonaComun"),
           o("liq", "Liquidacion"), o("not", "Notificacion")])
d.llamar("act", "res", "reservarZonaComun(id)")
d.llamar_y_retornar("res", "rez", "ReservaZonaComun(id, duenoReserva, zonaComun)", "")
d.llamar("res", "rez", "estaHabilitado(duenoReserva)")
d.llamar_y_retornar("rez", "res", "isActive()", "Boolean")
d.llamar_y_retornar("rez", "liq", "estaVencida()", "Boolean")
d.retornar("rez", "res", "Boolean")
d.retornar("res", "act", "ZonaComun")
d.llamar_y_retornar("act", "rez", "mostrarZonasComunes()", "List<ZonaComun>")
d.llamar_y_retornar("act", "rez", "intervaloValido()", "boolean")
d.llamar("act", "rez", "validarHorario()")
d.llamar_y_retornar("rez", "zon", "horarioCubre(inicio, fin)", "boolean")
d.retornar("rez", "act", "Boolean")
d.llamar_y_retornar("act", "rez", "cumpleAnticipacion(ahora, horas)", "boolean")
d.llamar_y_retornar("act", "rez", "cumpleDuracionMaxima(horas)", "boolean")
enviar(d, "act", "notificarReserva(reserva)", destinatarios_del_inmueble=False, apoyo=("cuando(reserva)", "String"))

d = nuevo("Cancelar reserva", [RESI, o("res", "Residente"), o("rez", "ReservaZonaComun")])
d.llamar("act", "res", "verReservas(id)")
d.propio("res", "exigirPropio(id)")
d.retornar("res", "act", "List<ReservaZonaComun>")
d.llamar("act", "res", "cancelarReservaZonaComun(idAdmin)")
d.llamar("res", "rez", "sePuedeCancelar(ahora)")
d.propio("rez", "estado(ahora)", "String")
d.retornar("rez", "res", "boolean")
d.retornar("res", "act", "")

d = nuevo("Verificar acceso a zona común",
          [VIGI, o("vig", "Vigilante"), o("usr", "Usuario"), o("ua", "UsuarioAutorizado"),
           o("rez", "ReservaZonaComun"), o("inm", "Inmueble"), o("not", "Notificacion")])
d.llamar("act", "vig", "verificarAccesoZonaComun(usuario)")
d.llamar_y_retornar("vig", "usr", "isActive()", "Boolean")
d.abrir("opt", "la persona es un invitado")
d.propio("vig", "existeInvitado(identificacion)", "Boolean")
d.llamar_y_retornar("vig", "ua", "autorizacionVigente()", "boolean")
d.llamar_y_retornar("vig", "ua", "buscarRelacionResidente()", "List<Residente>")
d.cerrar()
d.abrir("loop", "por cada reserva")
d.llamar_y_retornar("vig", "rez", "intervaloValido()", "boolean")
d.llamar_y_retornar("vig", "rez", "estado(ahora)", "String")
d.cerrar()
d.abrir("alt", "cuenta activa y con una reserva en curso")
d.retornar("vig", "act", "true", cerrar=False)
d.sino("cuenta inactiva o sin reserva en curso")
d.retornar("vig", "act", "false")
d.cerrar()
d.abrir("opt", "entra un invitado")
enviar(d, "act", "notificarVisita(inmueble)")
d.cerrar()

# ---------------------------------------------------------------- Notificaciones
d = nuevo("Consultar notificaciones", [TRES, o("res", "Residente"), o("usr", "Usuario")])
d.llamar("act", "res", "consultarNotificacion(inmueble, id)")
d.propio("res", "exigirPropio(id)")
d.propio("res", "exigirInmueble(otro)")
d.retornar("res", "act", "List<Notificacion>")
marcar_leida(d)

d = nuevo("Enviar notificación", [SIS, o("not", "Notificacion"), o("inm", "Inmueble")])
d.abrir("alt", "envío directo")
d.llamar_y_retornar("act", "not", "enviarNotificacion()", "Boolean")
d.sino("reserva registrada")
enviar(d, "act", "notificarReserva(reserva)", destinatarios_del_inmueble=False, apoyo=("cuando(reserva)", "String"))
d.sino("pago pendiente")
enviar(d, "act", "notificarPagoPendiente(inmueble)", apoyo=("pesos(valor)", "String"))
d.sino("pago actualizado")
enviar(d, "act", "notificarActualizacionDePago(inmueble, usuarios)")
d.sino("visita autorizada")
enviar(d, "act", "notificarVisita(inmueble)")
d.cerrar()

d = nuevo("Enviar notificaciones de vencimiento y mora",
          [SIS, o("liq", "Liquidacion"), o("not", "Notificacion"), o("inm", "Inmueble")])
d.abrir("loop", "cada día, por cada liquidación pendiente")
d.llamar_y_retornar("act", "liq", "estaVencida()", "Boolean")
d.abrir("opt", "liquidación vencida")
d.llamar_y_retornar("act", "liq", "marcarEnMora()", "")
d.cerrar()
d.cerrar()
d.abrir("opt", "recordatorio de pago al inmueble")
enviar(d, "act", "notificarPagoPendiente(inmueble)", apoyo=("pesos(valor)", "String"))
d.cerrar()

d = nuevo("Enviar recordatorio de reserva próxima", [SIS, o("res", "Residente"), o("not", "Notificacion")])
d.abrir("loop", "por cada residente con reservas")
d.llamar("act", "res", "verReservas(id)")
d.propio("res", "exigirPropio(id)")
d.retornar("res", "act", "List<ReservaZonaComun>")
d.abrir("opt", "la reserva empieza pronto")
enviar(d, "act", "notificarReserva(reserva)", destinatarios_del_inmueble=False, apoyo=("cuando(reserva)", "String"))
d.cerrar()
d.cerrar()

# ---------------------------------------------------------------- Otros casos de uso
d = nuevo("Cambiar contraseña", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "cambiarContrasena(inmueble, id)")
d.propio("usr", "esEste(id)", "boolean")
d.propio("usr", "isActive()", "Boolean")
d.propio("usr", "perteneceA(inmueble)", "boolean")
d.propio("usr", "cerrarSesion()")
d.retornar("usr", "act", "")

d = nuevo("Reembolsar pago", [SIS, o("pas", "PasarelaPago"), o("pag", "Pago"), o("inm", "Inmueble"),
                              o("not", "Notificacion")])
d.llamar("act", "pas", "reembolsar(pago)")
d.llamar("pas", "pag", "estaAprobado()")
d.propio("pag", "estaAprobado(pago)", "Boolean")
d.retornar("pag", "pas", "Boolean")
d.abrir("alt", "pago aprobado")
d.retornar("pas", "act", "true", cerrar=False)
d.sino("pago no aprobado")
d.retornar("pas", "act", "false")
d.cerrar()
d.abrir("opt", "pago reembolsado")
enviar(d, "act", "notificarActualizacionDePago(inmueble, usuarios)")
d.cerrar()

d = nuevo("Consultar dueño del carro", [TRES, o("aut", "Auto")])
d.llamar_y_retornar("act", "aut", "consultarDuenos(inmueble, id)", "List<Residente>")

d = nuevo("Consultar relacionados al inmueble",
          [TRES, o("inm", "Inmueble"), o("ua", "UsuarioAutorizado"), o("res", "Residente")])
d.llamar_y_retornar("act", "inm", "mostrarUsuarios(List<Usuario>)", "List<String>")
d.abrir("loop", "por cada usuario autorizado (invitado) del inmueble")
d.llamar("act", "ua", "buscarRelacionResidente()")
d.llamar_y_retornar("ua", "res", "tieneInvitado(invitado)", "boolean")
d.retornar("ua", "act", "List<Residente>")
d.cerrar()
d.abrir("alt", "el residente activa o desactiva un invitado")
d.llamar("act", "res", "cambiarEstadoInvitado(invitado, id)")
d.propio("res", "tieneInvitado(invitado)", "boolean")
d.retornar("res", "act", "Boolean")
d.sino("el residente activa o desactiva varios invitados")
d.llamar("act", "res", "cambiarEstadoInvitados(invitados)")
d.propio("res", "tieneInvitado(invitado)", "boolean")
d.retornar("res", "act", "Boolean")
d.cerrar()

d = nuevo("Consultar parqueaderos", [TRES, o("paq", "Paqueadero")])
d.llamar_y_retornar("act", "paq", "obtenerValorMensual()", "BigDecimal")

d = nuevo("Registrar incidencia operativa", [VIGI, o("vig", "Vigilante"), o("not", "Notificacion")])
d.llamar("act", "vig", "registrarIncidencia(descripcion)")
d.propio("vig", "isActive()", "Boolean")
d.propio("vig", "contratoVigente()", "boolean")
d.retornar("vig", "act", "")
d.llamar_y_retornar("act", "not", "Notificacion(id, usuarios, mensaje, tipoNotificacion, fechaEnvio)", "")
d.llamar("act", "not", "enviarNotificacion()")
d.abrir("alt", "hay destinatario y mensaje")
d.retornar("not", "act", "true", cerrar=False)
d.sino("sin destinatario o sin mensaje")
d.retornar("not", "act", "false")
d.cerrar()

d = nuevo("Consultar incidencias operativas", [ADMIN, o("adm", "Administrador"), o("usr", "Usuario")])
d.llamar("act", "adm", "consultarIncidencias()")
d.propio("adm", "exigirVigente()")
d.retornar("adm", "act", "List<Notificacion>")
marcar_leida(d)

d = nuevo("Generar reportes consolidados", [ADMIN, o("adm", "Administrador")])
d.llamar("act", "adm", "generarReportes()")
d.propio("adm", "exigirVigente()")
d.retornar("adm", "act", "")

d = nuevo("Configurar preferencias de notificación", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "configurarPreferencias(tipoNotificacion)")
d.propio("usr", "isActive()", "Boolean")
d.retornar("usr", "act", "")
d.llamar_y_retornar("act", "usr", "recibe(tipoNotificacion)", "boolean")


# =========================================================================================================
#  SVG: se dibuja desde las mismas celdas mxCell del XML de draw.io
# =========================================================================================================
def _estilo(texto):
    d = {}
    for par in (texto or "").split(";"):
        if "=" in par:
            k, v = par.split("=", 1)
            d[k] = v
    return d


def _lineas(valor):
    """Valor HTML de draw.io -> (lineas de texto plano, si viene en negrita)."""
    negrita = "<b>" in valor
    limpio = re.sub(r"</?b>", "", valor)
    return [html.unescape(x) for x in re.split(r"<br\s*/?>", limpio)], negrita


def _texto_svg(x, y, lineas, tam, ancla="start", negrita=False, cursiva=False, color="#000000"):
    """Un <text> con una <tspan> por linea; y es el borde superior del bloque."""
    atr = (f'font-family="Helvetica, Arial, sans-serif" font-size="{tam}" fill="{color}" text-anchor="{ancla}"'
           + (' font-weight="bold"' if negrita else "") + (' font-style="italic"' if cursiva else ""))
    paso = tam + 4
    tsp = "".join(f'<tspan x="{x:g}" y="{y + tam + i * paso - 2:g}">{esc(x_)}</tspan>'
                  for i, x_ in enumerate(lineas))
    return f"<text {atr}>{tsp}</text>"


def svg_de(d):
    """SVG (cadena) del diagrama d, a partir de su XML de draw.io."""
    modelo = ET.fromstring(d._xml_crudo()).find("mxGraphModel")
    ancho, alto = int(float(modelo.get("pageWidth"))), int(float(modelo.get("pageHeight")))
    partes = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" viewBox="0 0 {ancho} {alto}">',
        f"<title>{esc(d.titulo)}</title>",
        '<defs>'
        '<marker id="cerrada" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" '
        'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#000000"/></marker>'
        '<marker id="abierta" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" '
        'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#000000" stroke-width="1.2"/>'
        '</marker></defs>',
        f'<rect width="{ancho}" height="{alto}" fill="#ffffff"/>',
    ]
    for c in modelo.iter("mxCell"):
        st = _estilo(c.get("style"))
        g = c.find("mxGeometry")
        valor = c.get("value") or ""
        if g is None:
            continue
        if c.get("edge") == "1":
            pts = {p.get("as"): p for p in g.findall("mxPoint")}
            a, b = pts["sourcePoint"], pts["targetPoint"]
            ruta = [(float(a.get("x")), float(a.get("y")))]
            arr = g.find("Array")
            if arr is not None:
                ruta += [(float(p.get("x")), float(p.get("y"))) for p in arr.findall("mxPoint")]
            ruta.append((float(b.get("x")), float(b.get("y"))))
            punteada = st.get("dashed") == "1"
            sin_flecha = st.get("endArrow") == "none"
            color = st.get("strokeColor", "#000000")
            tipo = "abierta" if st.get("endFill") == "0" else "cerrada"
            marca = "" if sin_flecha else f' marker-end="url(#{tipo})"'
            puntos = " ".join(f"{px:g},{py:g}" for px, py in ruta)
            partes.append(f'<polyline points="{puntos}" fill="none" stroke="{color}" stroke-width="1"'
                          + (' stroke-dasharray="6 4"' if punteada else "") + marca + "/>")
            if valor:  # etiqueta centrada, apoyada encima de la linea
                lineas, _ = _lineas(valor)
                cx = (ruta[0][0] + ruta[-1][0]) / 2
                partes.append(_texto_svg(cx, ruta[0][1] - 4 - 15 * len(lineas), lineas, 11, "middle"))
            continue
        x, y = float(g.get("x")), float(g.get("y"))
        w, h = float(g.get("width")), float(g.get("height"))
        forma = st.get("shape", "")
        if forma == "umlActor":
            cx = x + w / 2
            partes.append(
                f'<circle cx="{cx:g}" cy="{y + 8:g}" r="8" fill="{st.get("fillColor", "#ffffff")}" '
                f'stroke="#36393d" stroke-width="1.2"/>'
                f'<path d="M{cx:g},{y + 16:g} V{y + 36:g} M{cx - 14:g},{y + 24:g} H{cx + 14:g} '
                f'M{cx:g},{y + 36:g} L{cx - 14:g},{y + h:g} M{cx:g},{y + 36:g} L{cx + 14:g},{y + h:g}" '
                f'fill="none" stroke="#36393d" stroke-width="1.2"/>')
            lineas, actual = [], ""
            for pal in html.unescape(valor).split(" "):
                if actual and len(actual) + len(pal) + 1 > 14:
                    lineas.append(actual)
                    actual = pal
                else:
                    actual = (actual + " " + pal).strip()
            lineas.append(actual)
            partes.append(_texto_svg(cx, y + h + 4, lineas, 11, "middle", negrita=True))
        elif forma == "umlLifeline":
            cx = x + w / 2
            partes.append(
                f'<line x1="{cx:g}" y1="{y + 40:g}" x2="{cx:g}" y2="{y + h:g}" stroke="#666666" '
                f'stroke-dasharray="6 4"/>'
                f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="40" fill="{st["fillColor"]}" '
                f'stroke="{st["strokeColor"]}"/>')
            partes.append(_texto_svg(cx, y + 11, [html.unescape(valor)], 12, "middle", negrita=True))
        elif forma == "umlFrame":
            partes.append(
                f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" fill="none" stroke="#36393d"/>'
                f'<path d="M{x:g},{y + 22:g} H{x + 45:g} L{x + 55:g},{y + 12:g} V{y:g}" fill="#ffffff" '
                f'stroke="#36393d"/>')
            partes.append(_texto_svg(x + 6, y + 4, [html.unescape(valor)], 11, negrita=True))
        elif "orthogonalPerimeter" in st.get("perimeter", ""):  # barra de activacion
            partes.append(f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" fill="#ffffff" stroke="#000000"/>')
        elif (c.get("style") or "").startswith("text;"):
            lineas, neg = _lineas(valor)
            partes.append(_texto_svg(x + 2, y + 2, lineas, int(st.get("fontSize", "11")),
                                     negrita=neg or st.get("fontStyle") == "1",
                                     cursiva=st.get("fontStyle") == "2", color=st.get("fontColor", "#000000")))
    partes.append("</svg>")
    return "\n".join(partes) + "\n"


def nombre_archivo(nombre):
    base = unicodedata.normalize("NFD", nombre.lower())
    base = "".join(ch for ch in base if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9]+", "-", base).strip("-")


def main():
    """Exporta los SVG (ver exportar_secuencia_svg.py). El XML solo se reescribe con --regenerar-xml: en draw.io se retoca a mano la posicion
    de las figuras y regenerarlo descarta esos retoques."""
    if "--regenerar-xml" in sys.argv:
        cuerpo = "".join(d.xml() for d in diagramas)
        os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
        with open(SALIDA, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n' + cuerpo + "</mxfile>\n")
        print(f"{len(diagramas)} diagramas (xml) -> {SALIDA}")
    else:
        print("XML sin tocar (usa --regenerar-xml para reescribirlo; se pierden los retoques hechos en draw.io)")
    if "--svg-propio" in sys.argv:
        # dibujo propio (sin internet); no refleja los retoques hechos a mano en draw.io
        os.makedirs(CARPETA_SVG, exist_ok=True)
        for d in diagramas:
            with open(os.path.join(CARPETA_SVG, nombre_archivo(d.nombre) + ".svg"), "w", encoding="utf-8") as f:
                f.write(svg_de(d))
        print(f"{len(diagramas)} svg -> {CARPETA_SVG}")
    else:
        # los SVG del README salen del XML real con el visor oficial de draw.io
        import subprocess
        subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                     "exportar_secuencia_svg.py")], check=False)


if __name__ == "__main__":
    main()
