#!/usr/bin/env python3
"""
Genera Diagramas/Secuencia/secuencia-casos-de-uso.xml (draw.io / diagrams.net):
un diagrama de secuencia por caso de uso, una pagina por diagrama.

Regla: los participantes son clases del diagrama de clases y los mensajes son SUS operaciones, con los
nombres y parametros tal como estan en el diagrama de clases. Los casos de uso son los del diagrama de
casos de uso (Diagramas/UML).

Uso:  python Diagramas/_fuente/gen_secuencia_casos_uso.py
"""
import html
import math
import os

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SALIDA = os.path.join(RAIZ, "Diagramas", "Secuencia", "secuencia-casos-de-uso.xml")

ESPACIO = 270          # distancia entre lifelines
ANCHO_CAJA = 150       # ancho del encabezado de cada lifeline
TOP = 60               # y del encabezado
Y0 = 170               # y de la primera interaccion
CHAR = 6.4             # ancho aproximado de un caracter (px) a 11 px

AZUL = "#cce5ff"
BORDE = "#36393d"


def esc(s):
    return html.escape(s, quote=True)


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
        if xb > xa:
            sx, tx = xa + 5, xb - 5
        else:
            sx, tx = xa - 5, xb + 5
        estilo = ("html=1;verticalAlign=bottom;rounded=0;fontSize=11;strokeColor=#000000;"
                  + ("dashed=1;endArrow=open;endFill=0;endSize=8;" if retorno
                     else "endArrow=block;endFill=1;endSize=8;"))
        self.celdas_msg.append(
            f'<mxCell id="{self._id("m")}" value="{esc("<br>".join(lineas))}" style="{estilo}" '
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
        self._texto(x1 + 44, y - 2, ancho_txt, alto + 4, "<br>".join(lineas), "left")
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
            f'<mxCell id="{self._id("f")}" value="{esc(f["tipo"])}" style="shape=umlFrame;whiteSpace=wrap;'
            f'html=1;pointerEvents=0;fillColor=none;strokeColor=#36393d;fontStyle=1;fontSize=11;width=55;'
            f'height=22;" vertex="1" parent="1"><mxGeometry x="{x1}" y="{top}" width="{ancho}" '
            f'height="{bottom - top}" as="geometry"/></mxCell>')
        self.celdas_txt.append(
            f'<mxCell id="{self._id("g")}" value="{esc("[" + f["cond"] + "]")}" style="text;html=1;align=left;'
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
                f'<mxCell id="{self._id("g")}" value="{esc("[" + cond + "]")}" style="text;html=1;align=left;'
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
        fin = self.y + 30
        celdas = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
        # titulo
        celdas.append(
            f'<mxCell id="titulo" value="{esc("<b>" + self.titulo + "</b>")}" style="text;html=1;align=left;'
            f'verticalAlign=top;whiteSpace=wrap;fontSize=15;" vertex="1" parent="1">'
            f'<mxGeometry x="20" y="12" width="{max(500, self.max_x - 40)}" height="26" as="geometry"/></mxCell>')
        if self.nota:
            celdas.append(
                f'<mxCell id="nota" value="{esc(self.nota)}" style="text;html=1;align=left;verticalAlign=top;'
                f'whiteSpace=wrap;fontSize=11;fontColor=#555555;" vertex="1" parent="1">'
                f'<mxGeometry x="20" y="34" width="{max(500, self.max_x - 40)}" height="18" as="geometry"/></mxCell>')
        # lifelines
        for pid in self.orden:
            p = self.parts[pid]
            cx = p["cx"]
            if p["tipo"] == "actor":
                celdas.append(
                    f'<mxCell id="L-{pid}" value="{esc(p["etiqueta"])}" style="shape=umlActor;'
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
                    f'<mxCell id="L-{pid}" value="{esc(p["etiqueta"])}" style="shape=umlLifeline;'
                    f'perimeter=lifelinePerimeter;whiteSpace=wrap;html=1;container=0;dropTarget=0;'
                    f'collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;'
                    f'fillColor={AZUL};strokeColor={BORDE};fontStyle=1;fontSize=12;" vertex="1" parent="1">'
                    f'<mxGeometry x="{cx - ANCHO_CAJA // 2}" y="{TOP}" width="{ANCHO_CAJA}" '
                    f'height="{fin - TOP}" as="geometry"/></mxCell>')
        celdas += self.celdas_frames + self.celdas_act + self.celdas_msg + self.celdas_txt
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


def notificar(d, de, tipo_llamada, inmueble=True):
    """de -> Notificacion.<tipo_llamada>; la notificacion toma los usuarios del Inmueble y se envia."""
    d.llamar(de, "not", tipo_llamada)
    if inmueble:
        d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
    d.propio("not", "enviarNotificacion()", "Boolean")
    d.retornar("not", de, "")


# ---------------------------------------------------------------- Modulo transversal
d = nuevo("Iniciar sesión", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "iniciarSesion(correo, contrasena)")
d.propio("usr", "isActive()", "Boolean")
d.abrir("alt", "cuenta activa y credenciales correctas")
d.retornar("usr", "act", "usuario", cerrar=False)
d.sino("cuenta inactiva o credenciales incorrectas")
d.retornar("usr", "act", "error: no autenticado")
d.cerrar()

d = nuevo("Activar cuenta creada", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "activarCuenta(correo, contrasena)")
d.propio("usr", "isActive()", "Boolean")
d.abrir("alt", "credenciales correctas y cuenta aún inactiva")
d.retornar("usr", "act", "true", cerrar=False)
d.sino("credenciales incorrectas o cuenta ya activa")
d.retornar("usr", "act", "false")
d.cerrar()

d = nuevo("Recuperar contraseña", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "recuperarContrasena()")
d.propio("usr", "cerrarSesion()")
d.retornar("usr", "act", "confirmación de envío")

# ---------------------------------------------------------------- Actores y configuracion base
d = nuevo("Gestionar cuentas de usuario",
          [ADMIN, o("adm", "Administrador"), o("usr", "Usuario"), o("res", "Residente")])
d.llamar("act", "adm", "gestionarCuentaUsuario()")
d.llamar_y_retornar("adm", "usr", "Usuario(id, identificacion, nombreCompleto, fechaNacimiento, "
                    "correoElectronico, contrasena, tipoUsuario)", "cuenta creada")
d.retornar("adm", "act", "cuenta de usuario")
d.abrir("opt", "activar o desactivar una cuenta existente")
d.llamar("act", "adm", "modificarActive(identificacion, idToken)")
d.llamar_y_retornar("adm", "usr", "isActive()", "Boolean")
d.retornar("adm", "act", "NoAdmin")
d.cerrar()
d.abrir("opt", "activar o desactivar invitados de un residente")
d.llamar("act", "adm", "gestionarCuentaUsuario()")
d.abrir("alt", "un invitado")
d.llamar_y_retornar("adm", "res", "cambiarEstadoInvitado(invitado, id)", "Boolean")
d.sino("varios invitados")
d.llamar_y_retornar("adm", "res", "cambiarEstadoInvitados(invitados)", "Boolean")
d.cerrar()
d.retornar("adm", "act", "estado de los invitados")
d.cerrar()

d = nuevo("Configurar multas", [ADMIN, o("adm", "Administrador"), o("liq", "Liquidacion")])
d.llamar("act", "adm", "configurarMultas(inmueble, adminId)")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.retornar("adm", "act", "liquidación con el nuevo valor de multas")

d = nuevo("Configurar tarifas",
          [ADMIN, o("adm", "Administrador"), o("paq", "Paqueadero"), o("liq", "Liquidacion")])
d.llamar("act", "adm", "configurarTarifas(inmueble, adminId)")
d.llamar_y_retornar("adm", "paq", "obtenerValorMensual()", "BigDecimal")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.retornar("adm", "act", "liquidación con las nuevas tarifas")

d = nuevo("Configurar zonas comunes", [ADMIN, o("adm", "Administrador"), o("zon", "ZonaComun")])
d.llamar("act", "adm", "ConfigurarZonasComunes(inmueble, adminId)")
d.abrir("loop", "por cada zona común a configurar")
d.llamar_y_retornar("adm", "zon", "cambiarEstado()", "isActive actualizado")
d.cerrar()
d.retornar("adm", "act", "zonas comunes del inmueble")

d = nuevo("Configurar reglas de reserva",
          [ADMIN, o("adm", "Administrador"), o("reg", "ReglaReserva"), o("rom", "Rompimiento")])
d.llamar("act", "adm", "ConfigurarNuevaReglaReserva(inmueble, adminId)")
d.llamar_y_retornar("adm", "reg", "actualizarReglas(nuevaAnticipacion, nuevaDuracion)", "regla actualizada")
d.abrir("loop", "por cada zona común a la que se aplica la regla")
d.llamar_y_retornar("adm", "rom", "aplicarRegla()", "activa = true")
d.cerrar()
d.abrir("opt", "retirar la regla de una zona")
d.llamar_y_retornar("adm", "rom", "inactivarRegla()", "activa = false")
d.cerrar()
d.retornar("adm", "act", "regla de reserva configurada")

# ---------------------------------------------------------------- Pagos
d = nuevo("Consultar estado de cuenta e historial de pagos",
          [RESI, o("res", "Residente"), o("inm", "Inmueble"), o("liq", "Liquidacion"), o("pag", "Pago")])
d.llamar("act", "res", "consultarEstadoCuenta(inmueble, id)")
d.llamar("res", "inm", "pagoAlDiaAdministracion(liquidacion)")
d.llamar_y_retornar("inm", "liq", "estaVencida()", "Boolean")
d.retornar("inm", "res", "boolean")
d.llamar_y_retornar("res", "liq", "calcularTotal()", "BigDecimal")
d.retornar("res", "act", "Liquidacion")
d.llamar("act", "res", "consultarHistorialPago(id)")
d.abrir("loop", "por cada pago registrado del inmueble")
d.llamar_y_retornar("res", "pag", "estaAprobado(pago)", "Boolean")
d.abrir("opt", "pago aprobado")
d.llamar_y_retornar("res", "pag", "generarComprobante()", "String")
d.cerrar()
d.cerrar()
d.retornar("res", "act", "List<Pago>")

d = nuevo("Realizar pagos",
          [RESI, o("pag", "Pago"), o("liq", "Liquidacion"), o("pas", "PasarelaPago"), o("not", "Notificacion"),
           o("inm", "Inmueble")])
d.llamar("act", "pag", "registrarPago(inmueble)")
d.llamar_y_retornar("pag", "liq", "calcularTotal()", "BigDecimal")
d.propio("pag", "validarMonto(liquidacion)", "Boolean")
d.abrir("alt", "monto válido")
d.llamar("pag", "pas", "iniciarTransaccion(pago)")
d.retornar("pas", "pag", "String (URL del checkout)")
d.retornar("pag", "act", "Pago PENDIENTE y URL del checkout")
d.mensaje("act", "pas", "paga en la pasarela (PSE, tarjeta o Nequi)")
d.propio("pas", "confirmarTransaccion(referencia)", "Boolean")
d.llamar_y_retornar("pas", "pag", "estaAprobado(pago)", "Boolean")
d.abrir("alt", "transacción aprobada")
d.llamar("pas", "pag", "actualizarEstado(pago)")
d.llamar_y_retornar("pag", "liq", "marcarAlDia()", "")
d.retornar("pag", "pas", "Liquidacion")
d.llamar_y_retornar("pas", "pag", "generarComprobante()", "String")
d.propio("pas", "notificarResultado(pago)")
d.llamar("pas", "not", "notificarActualizacionDePago(inmueble, usuarios)")
d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "pas", "")
d.sino("transacción rechazada")
d.llamar_y_retornar("pas", "pag", "anularPago()", "estado = ANULADO")
d.cerrar()
d.llamar_y_retornar("act", "pas", "consultarEstado(referencia)", "String")
d.sino("monto inválido")
d.retornar("pag", "act", "error: el monto no coincide con la liquidación")
d.cerrar()

d = nuevo("Conciliar pagos de administración",
          [ADMIN, o("adm", "Administrador"), o("pag", "Pago"), o("liq", "Liquidacion"), o("not", "Notificacion"),
           o("inm", "Inmueble")])
d.llamar("act", "adm", "conciliarPagosAdministracion(inmueble, adminId)")
d.llamar_y_retornar("adm", "pag", "registrarPago(inmueble)", "Pago")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.llamar_y_retornar("adm", "pag", "validarMonto(liquidacion)", "Boolean")
d.abrir("alt", "monto igual al saldo pendiente")
d.llamar_y_retornar("adm", "pag", "estaAprobado(pago)", "Boolean")
d.llamar("adm", "pag", "actualizarEstado(pago)")
d.llamar_y_retornar("pag", "liq", "marcarAlDia()", "")
d.retornar("pag", "adm", "Liquidacion")
d.llamar("adm", "not", "notificarActualizacionDePago(inmueble, usuarios)")
d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "adm", "")
d.retornar("adm", "act", "Pago", cerrar=False)
d.sino("monto distinto al saldo pendiente")
d.llamar_y_retornar("adm", "pag", "anularPago()", "estado = ANULADO")
d.retornar("adm", "act", "error: el monto debe ser igual al saldo")
d.cerrar()

d = nuevo("Generar liquidación mensual",
          [SIS, o("liq", "Liquidacion"), o("paq", "Paqueadero")])
d.abrir("loop", "cada mes, por cada inmueble activo")
d.llamar("act", "liq", "generarLiquidacion(inmueble)")
d.llamar_y_retornar("liq", "paq", "obtenerValorMensual()", "BigDecimal")
d.propio("liq", "calcularTotal()", "BigDecimal")
d.retornar("liq", "act", "Liquidacion")
d.cerrar()

# ---------------------------------------------------------------- Comunicacion
d = nuevo("Consultar comunicado oficial",
          [RESI, o("res", "Residente"), o("inm", "Inmueble"), o("not", "Notificacion"), o("usr", "Usuario")])
d.llamar("act", "res", "consultarNotificacion(inmueble, id)")
d.llamar("res", "inm", "notificaciones")
d.retornar("inm", "res", "List<Notificacion>")
d.retornar("res", "act", "List<Notificacion>")
d.llamar_y_retornar("act", "usr", "marcarNotificacionLeida(inmueble, id)", "notificacionLeida = true")

d = nuevo("Publicar comunicado oficial",
          [ADMIN, o("inm", "Inmueble"), o("not", "Notificacion")])
d.llamar_y_retornar("act", "inm", "mostrarUsuarios(List)", "List<Usuario>")
d.llamar_y_retornar("act", "not", "Notificacion(id, usuarios, mensaje, tipoNotificacion, fechaEnvio)",
                    "notificación")
d.llamar("act", "not", "enviarNotificacion()")
d.abrir("alt", "hay destinatarios y mensaje")
d.retornar("not", "act", "true", cerrar=False)
d.sino("sin destinatarios o sin mensaje")
d.retornar("not", "act", "false")
d.cerrar()

d = nuevo("Consultar confirmación de lectura",
          [ADMIN, o("adm", "Administrador"), o("not", "Notificacion"), o("usr", "Usuario")])
d.llamar("act", "adm", "consultarConfirmacionLectura")
d.llamar_y_retornar("adm", "not", "usuarios", "List<Usuario>")
d.abrir("loop", "por cada destinatario de la notificación")
d.llamar_y_retornar("adm", "usr", "notificacionLeida", "Boolean")
d.cerrar()
d.retornar("adm", "act", "lectura de cada destinatario")

# ---------------------------------------------------------------- Zonas comunes
d = nuevo("Consultar calendario de disponibilidad",
          [("act", "actor", "Residente o Vigilante"), o("res", "Residente"), o("vig", "Vigilante"),
           o("rez", "ReservaZonaComun")])
d.abrir("alt", "actor = Residente")
d.llamar("act", "res", "verReservas(id)")
d.llamar_y_retornar("res", "rez", "mostrarZonasComunes()", "List")
d.abrir("loop", "por cada zona común")
d.llamar_y_retornar("res", "rez", "verificarDisponibilidad(hora)", "Boolean")
d.llamar_y_retornar("res", "rez", "validarHorario(hora)", "Boolean")
d.cerrar()
d.retornar("res", "act", "List<ReservaZonaComun>")
d.sino("actor = Vigilante")
d.llamar("act", "vig", "consultarReservas()")
d.llamar_y_retornar("vig", "rez", "mostrarZonasComunes()", "List")
d.abrir("loop", "por cada zona común")
d.llamar_y_retornar("vig", "rez", "verificarDisponibilidad(hora)", "Boolean")
d.llamar_y_retornar("vig", "rez", "validarHorario(hora)", "Boolean")
d.cerrar()
d.retornar("vig", "act", "List")
d.cerrar()

d = nuevo("Reservar zona común",
          [RESI, o("res", "Residente"), o("rez", "ReservaZonaComun"), o("inm", "Inmueble"),
           o("liq", "Liquidacion"), o("not", "Notificacion")])
d.llamar("act", "res", "reservarZonaComun(id)")
d.llamar_y_retornar("res", "rez", "mostrarZonasComunes()", "List")
d.llamar_y_retornar("res", "rez", "verificarDisponibilidad(hora)", "Boolean")
d.llamar_y_retornar("res", "rez", "validarHorario(hora)", "Boolean")
d.llamar("res", "rez", "estaHabilitado(duenoReserva)")
d.llamar("rez", "inm", "pagoAlDiaAdministracion(liquidacion)")
d.llamar_y_retornar("inm", "liq", "estaVencida()", "Boolean")
d.retornar("inm", "rez", "boolean")
d.retornar("rez", "res", "Boolean")
d.abrir("alt", "zona disponible, horario válido y residente habilitado")
d.llamar("res", "not", "notificarReserva(reserva)")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "res", "")
d.retornar("res", "act", "ZonaComun reservada", cerrar=False)
d.sino("alguna condición no se cumple")
d.retornar("res", "act", "error: reserva rechazada")
d.cerrar()

d = nuevo("Cancelar reserva", [RESI, o("res", "Residente"), o("rez", "ReservaZonaComun")])
d.llamar("act", "res", "verReservas(id)")
d.retornar("res", "act", "List<ReservaZonaComun>")
d.llamar("act", "res", "cancelarReservaZonaComun(idAdmin)")
d.abrir("alt", "reserva propia y aún no iniciada")
d.retornar("res", "act", "reserva cancelada", cerrar=False)
d.sino("reserva ajena o ya iniciada")
d.retornar("res", "act", "error: no se puede cancelar")
d.cerrar()

# ---------------------------------------------------------------- Porteria
d = nuevo("Verificar acceso a zona común",
          [VIGI, o("vig", "Vigilante"), o("usr", "Usuario"), o("ua", "UsuarioAutorizado"),
           o("rez", "ReservaZonaComun"), o("not", "Notificacion"), o("inm", "Inmueble")])
d.llamar("act", "vig", "verificarAccesoZonaComun(usuario)")
d.llamar_y_retornar("vig", "usr", "isActive()", "Boolean")
d.abrir("alt", "cuenta inactiva")
d.retornar("vig", "act", "false", cerrar=False)
d.sino("cuenta activa")
d.propio("vig", "existeInvitado(identificacion)", "Boolean")
d.abrir("opt", "la persona es un invitado")
d.llamar_y_retornar("vig", "ua", "buscarRelacionResidente()", "List")
d.cerrar()
d.llamar_y_retornar("vig", "rez", "validarHorario(hora)", "Boolean")
d.abrir("alt", "reserva en curso")
d.abrir("opt", "la persona es un invitado")
d.llamar("vig", "not", "notificarVisita(inmueble)")
d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "vig", "")
d.cerrar()
d.retornar("vig", "act", "true", cerrar=False)
d.sino("sin reserva en curso")
d.retornar("vig", "act", "false")
d.cerrar()
d.cerrar()

# ---------------------------------------------------------------- Notificaciones
d = nuevo("Consultar notificaciones",
          [TRES, o("res", "Residente"), o("inm", "Inmueble"), o("not", "Notificacion"), o("usr", "Usuario")])
d.llamar("act", "res", "consultarNotificacion(inmueble, id)")
d.llamar("res", "inm", "notificaciones")
d.retornar("inm", "res", "List<Notificacion>")
d.retornar("res", "act", "List<Notificacion>")
d.llamar_y_retornar("act", "usr", "marcarNotificacionLeida(inmueble, id)", "notificacionLeida = true")

d = nuevo("Enviar notificación", [SIS, o("not", "Notificacion"), o("inm", "Inmueble")])
d.abrir("alt", "envío directo")
d.llamar("act", "not", "enviarNotificacion()")
d.retornar("not", "act", "Boolean")
d.sino("reserva registrada")
notificar(d, "act", "notificarReserva(reserva)", inmueble=False)
d.sino("pago pendiente")
notificar(d, "act", "notificarPagoPendiente(inmueble)")
d.sino("pago actualizado")
notificar(d, "act", "notificarActualizacionDePago(inmueble, usuarios)")
d.sino("visita autorizada")
notificar(d, "act", "notificarVisita(inmueble)")
d.cerrar()

d = nuevo("Enviar notificaciones de vencimiento y mora",
          [SIS, o("inm", "Inmueble"), o("liq", "Liquidacion"), o("not", "Notificacion")])
d.abrir("loop", "cada día, por cada inmueble con liquidación vigente")
d.llamar("act", "inm", "pagoAlDiaAdministracion(liquidacion)")
d.llamar_y_retornar("inm", "liq", "estaVencida()", "Boolean")
d.retornar("inm", "act", "boolean")
d.abrir("opt", "liquidación vencida y sin pagar")
d.llamar_y_retornar("act", "liq", "marcarEnMora()", "estado = EN_MORA")
d.llamar("act", "not", "notificarPagoPendiente(inmueble)")
d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "act", "")
d.cerrar()
d.cerrar()

d = nuevo("Enviar recordatorio de reserva próxima",
          [SIS, o("res", "Residente"), o("rez", "ReservaZonaComun"), o("not", "Notificacion")])
d.abrir("loop", "por cada residente con reservas")
d.llamar_y_retornar("act", "res", "verReservas(id)", "List<ReservaZonaComun>")
d.abrir("opt", "la reserva empieza pronto")
d.llamar("act", "not", "notificarReserva(reserva)")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "act", "")
d.cerrar()
d.cerrar()


# ---------------------------------------------------------------- Casos de uso agregados despues
d = nuevo("Cambiar contraseña", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "cambiarContrasena(inmueble, id)")
d.propio("usr", "isActive()", "Boolean")
d.abrir("alt", "cuenta activa y contraseña actual correcta")
d.propio("usr", "cerrarSesion()")
d.retornar("usr", "act", "contraseña actualizada", cerrar=False)
d.sino("cuenta inactiva o contraseña actual incorrecta")
d.retornar("usr", "act", "error: no se puede cambiar la contraseña")
d.cerrar()

d = nuevo("Reembolsar pago",
          [SIS, o("pas", "PasarelaPago"), o("pag", "Pago"), o("not", "Notificacion"), o("inm", "Inmueble")])
d.llamar("act", "pas", "reembolsar(pago)")
d.llamar_y_retornar("pas", "pag", "estaAprobado(pago)", "Boolean")
d.abrir("alt", "pago aprobado")
d.propio("pas", "notificarResultado(pago)")
d.llamar("pas", "not", "notificarActualizacionDePago(inmueble, usuarios)")
d.llamar_y_retornar("not", "inm", "mostrarUsuarios(List)", "List")
d.propio("not", "enviarNotificacion()", "Boolean")
d.retornar("not", "pas", "")
d.retornar("pas", "act", "true", cerrar=False)
d.sino("pago no aprobado")
d.retornar("pas", "act", "false")
d.cerrar()

d = nuevo("Consultar dueño del carro", [TRES, o("aut", "Auto"), o("inm", "Inmueble")])
d.llamar("act", "aut", "consultarDuenos(inmueble, id)")
d.llamar_y_retornar("aut", "inm", "mostrarUsuarios(List)", "List")
d.retornar("aut", "act", "List<Residente>")

d = nuevo("Consultar relacionados al inmueble",
          [TRES, o("inm", "Inmueble"), o("ua", "UsuarioAutorizado")])
d.llamar_y_retornar("act", "inm", "mostrarUsuarios(List)", "List<Usuario>")
d.abrir("loop", "por cada usuario autorizado (invitado) del inmueble")
d.llamar_y_retornar("act", "ua", "buscarRelacionResidente()", "List")
d.cerrar()

d = nuevo("Consultar parqueaderos", [TRES, o("inm", "Inmueble"), o("paq", "Paqueadero")])
d.llamar_y_retornar("act", "inm", "parqueadero", "Paqueadero")
d.llamar_y_retornar("act", "paq", "obtenerValorMensual()", "BigDecimal")


# ---------------------------------------------------------------- Incidencias (Porteria)
d = nuevo("Registrar incidencia operativa",
          [VIGI, o("vig", "Vigilante"), o("not", "Notificacion"), o("usr", "Usuario")])
d.llamar("act", "vig", "registrarIncidencia(descripcion)")
d.llamar_y_retornar("vig", "not", "Notificacion(id, usuarios, mensaje, tipoNotificacion, fechaEnvio)",
                    "notificación tipo INCIDENCIA")
d.llamar("vig", "not", "enviarNotificacion()")
d.abrir("alt", "hay destinatario y mensaje")
d.retornar("not", "vig", "true", cerrar=False)
d.sino("sin destinatario o sin mensaje")
d.retornar("not", "vig", "false")
d.cerrar()
d.retornar("vig", "act", "incidencia registrada")

d = nuevo("Consultar incidencias operativas",
          [ADMIN, o("adm", "Administrador"), o("not", "Notificacion"), o("usr", "Usuario")])
d.llamar("act", "adm", "consultarIncidencias()")
d.llamar_y_retornar("adm", "not", "tipoNotificacion = INCIDENCIA", "List<Notificacion>")
d.retornar("adm", "act", "List<Notificacion>")
d.llamar_y_retornar("act", "usr", "marcarNotificacionLeida(inmueble, id)", "notificacionLeida = true")


# ---------------------------------------------------------------- Reportes y preferencias
d = nuevo("Generar reportes consolidados",
          [ADMIN, o("adm", "Administrador"), o("inm", "Inmueble"), o("liq", "Liquidacion"), o("pag", "Pago")])
d.llamar("act", "adm", "generarReportes()")
d.abrir("loop", "por cada inmueble")
d.llamar("adm", "inm", "pagoAlDiaAdministracion(liquidacion)")
d.llamar_y_retornar("inm", "liq", "estaVencida()", "Boolean")
d.retornar("inm", "adm", "boolean")
d.llamar_y_retornar("adm", "liq", "calcularTotal()", "BigDecimal")
d.abrir("loop", "por cada pago del inmueble")
d.llamar_y_retornar("adm", "pag", "estaAprobado(pago)", "Boolean")
d.cerrar()
d.cerrar()
d.retornar("adm", "act", "reporte consolidado (recaudo, mora y pendientes)")

d = nuevo("Configurar preferencias de notificación", [TRES, o("usr", "Usuario")])
d.llamar("act", "usr", "configurarPreferencias(tipoNotificacion)")
d.propio("usr", "isActive()", "Boolean")
d.abrir("alt", "cuenta activa")
d.retornar("usr", "act", "preferenciasNotificacion actualizadas", cerrar=False)
d.sino("cuenta inactiva")
d.retornar("usr", "act", "error: cuenta inactiva")
d.cerrar()


def main():
    cuerpo = "".join(d.xml() for d in diagramas)
    os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
    with open(SALIDA, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n' + cuerpo + "</mxfile>\n")
    print(f"{len(diagramas)} diagramas -> {SALIDA}")


if __name__ == "__main__":
    main()
