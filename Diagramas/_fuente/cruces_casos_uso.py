"""Cuenta cruces en el diagrama de casos de uso (lineas rectas actor-caso y include/extend).

Uso: python cruces_casos_uso.py casos-de-uso.drawio.xml
Imprime los pares de lineas que se cruzan y las lineas que atraviesan un actor o un caso de uso ajeno.
Los extremos se calculan como draw.io: punto fijo (entryX/exitX) si existe; si no, el borde de la figura
(elipse o rectangulo) en la recta entre centros.
"""
import math
import re
import sys
import xml.etree.ElementTree as ET

raiz = ET.parse(sys.argv[1]).getroot()
celdas = {c.get("id"): c for c in raiz.iter("mxCell")}


def caja(cid):
    c = celdas[cid]
    g = c.find("mxGeometry")
    x, y = float(g.get("x", 0)), float(g.get("y", 0))
    p = c.get("parent")
    while p in celdas and celdas[p].get("vertex") == "1":
        gp = celdas[p].find("mxGeometry")
        x += float(gp.get("x", 0)); y += float(gp.get("y", 0))
        p = celdas[p].get("parent")
    return x, y, float(g.get("width", 0)), float(g.get("height", 0))


def estilo(c):
    return dict(kv.split("=", 1) for kv in (c.get("style") or "").split(";") if "=" in kv)


def es_figura(c):
    s = c.get("style") or ""
    return c.get("vertex") == "1" and ("umlActor" in s or "ellipse" in s)


def nombre(cid):
    v = re.sub(r"<[^>]+>", " ", celdas[cid].get("value") or "").split()
    return " ".join(v)[:28] or cid


def borde(cid, hacia):
    x, y, w, h = caja(cid)
    cx, cy = x + w / 2, y + h / 2
    dx, dy = hacia[0] - cx, hacia[1] - cy
    if dx == dy == 0:
        return cx, cy
    if "ellipse" in (celdas[cid].get("style") or ""):
        t = 1 / math.sqrt((dx / (w / 2)) ** 2 + (dy / (h / 2)) ** 2)
    else:
        t = min(abs((w / 2) / dx) if dx else 1e9, abs((h / 2) / dy) if dy else 1e9)
    return cx + dx * t, cy + dy * t


def punto_fijo(cid, st, pref):
    if pref + "X" not in st:
        return None
    x, y, w, h = caja(cid)
    return x + float(st[pref + "X"]) * w, y + float(st[pref + "Y"]) * h


lineas = []
for cid, c in celdas.items():
    if c.get("edge") != "1":
        continue
    s, t = c.get("source"), c.get("target")
    if not (s and t):
        print("SUELTA", cid)
        continue
    st = estilo(c)
    xs, ys, ws, hs = caja(s); xt, yt, wt, ht = caja(t)
    centro_s, centro_t = (xs + ws / 2, ys + hs / 2), (xt + wt / 2, yt + ht / 2)
    p1 = punto_fijo(s, st, "exit"); p2 = punto_fijo(t, st, "entry")
    p1 = p1 or borde(s, p2 or centro_t)
    p2 = p2 or borde(t, p1)
    pts = [p1] + [(float(p.get("x")), float(p.get("y"))) for p in c.iter("mxPoint") if p.get("as") is None] + [p2]
    lineas.append((cid, s, t, pts))


def corta(a, b, c, d):
    def o(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    return o(a, b, c) * o(a, b, d) < -1e-6 and o(c, d, a) * o(c, d, b) < -1e-6


def tramos(pts):
    return list(zip(pts, pts[1:]))


cruces = 0
for i, (e1, s1, t1, p1) in enumerate(lineas):
    for e2, s2, t2, p2 in lineas[i + 1:]:
        if {s1, t1} & {s2, t2}:
            continue
        if any(corta(a, b, c, d) for a, b in tramos(p1) for c, d in tramos(p2)):
            cruces += 1
            print(f"CRUCE  {nombre(s1)} -> {nombre(t1)}   x   {nombre(s2)} -> {nombre(t2)}")

atraviesa = 0
for e, s, t, pts in lineas:
    for cid, c in celdas.items():
        if cid in (s, t) or not es_figura(c):
            continue
        x, y, w, h = caja(cid)
        x0, y0, x1, y1 = x + 3, y + 3, x + w - 3, y + h - 3
        esq = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
        dentro = lambda p: x0 < p[0] < x1 and y0 < p[1] < y1
        if any(dentro(a) or dentro(b) or any(corta(a, b, esq[k], esq[(k + 1) % 4]) for k in range(4))
               for a, b in tramos(pts)):
            atraviesa += 1
            print(f"ATRAVIESA  {nombre(s)} -> {nombre(t)}  pasa por  {nombre(cid)}")

print(f"\n{len(lineas)} lineas, {cruces} cruces, {atraviesa} atraviesan figuras")
print("SIN PROBLEMAS" if cruces == atraviesa == 0 else "HAY PROBLEMAS")
