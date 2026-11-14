"""Diagrama de paquetes del backend con rutas ortogonales que no atraviesan carpetas.
Genera paquetes-backend.xml (draw.io) y paquetes-backend.svg (para la captura PNG)."""
import sys, os, heapq
from xml.sax.saxutils import escape

OUT = sys.argv[1]
G = 20  # rejilla (separacion minima entre lineas)

PK = {  # nombre: (clases, descripcion)
    "scheduler": (1, "tarea nocturna de liquidaciones"),
    "controller": (16, "controladores REST"),
    "config": (9, "seguridad, CORS, cache"),
    "service": (16, "interfaces de negocio"),
    "security.annotation": (7, "anotaciones de rol"),
    "service.impl": (16, "implementaciones de los servicios"),
    "factory": (1, "creacion de entidades"),
    "policy": (5, "reglas de estado y acceso"),
    "integration.wompi": (4, "cliente pasarela Wompi"),
    "mail": (2, "envio de correo"),
    "cache": (1, "cache de consultas"),
    "mapper": (9, "fila SQL a respuesta"),
    "bootstrap": (4, "datos iniciales y config."),
    "service.support": (1, "apoyo a servicios"),
    "security": (9, "JWT y usuario actual"),
    "repository": (15, "SQL nativo (JdbcTemplate)"),
    "model": (17, "entidades de dominio"),
    "dto.request": (12, "objetos de peticion"),
    "dto.response": (13, "objetos de respuesta"),
    "exception": (9, "excepciones y manejador"),
}
DEPS = {
    "scheduler": ["service"],
    "controller": ["service", "security.annotation", "repository", "security"],
    "config": ["security", "cache"],
    "service": ["security"],
    "service.impl": ["service", "factory", "policy", "service.support", "integration.wompi", "bootstrap",
                     "mail", "cache", "mapper", "repository", "model", "security"],
    "factory": ["mapper", "model"],
    "policy": ["repository", "model", "security"],
    "integration.wompi": ["bootstrap"],
    "bootstrap": ["service.support", "repository", "model"],
    "service.support": ["repository", "model"],
    "mapper": ["repository", "model", "security"],
    "repository": ["model"],
}
TRANSV = {
    "dto.request": ["config", "controller", "service", "service.impl", "factory", "dto.response"],
    "dto.response": ["controller", "service", "service.impl", "mapper"],
    "exception": ["config", "controller", "dto.request", "factory", "integration.wompi", "policy",
                  "security", "service.impl"],
}

TAB_W, TAB_H, BODY_H = 70, 20, 70
BH = TAB_H + BODY_H
R = [120, 360, 600, 840, 1080, 1320, 1560, 1800]   # filas (pasillos de 130 px)
BOX = {  # nombre: (x, y, ancho)
    "scheduler": (200, R[0], 200), "controller": (660, R[0], 220), "config": (1240, R[0], 200),
    "service": (380, R[1], 200), "security.annotation": (960, R[1], 200),
    "service.impl": (420, R[2], 680),
    "factory": (40, R[3], 200), "policy": (320, R[3], 200), "integration.wompi": (720, R[3], 200),
    "mail": (1060, R[3], 200), "cache": (1380, R[3], 200),
    "mapper": (120, R[4], 200), "bootstrap": (720, R[4], 200),
    "service.support": (720, R[5], 200), "security": (1240, R[5], 200),
    "repository": (400, R[6], 260),
    "model": (320, R[7], 440),
    "dto.request": (900, R[7], 200), "dto.response": (1140, R[7], 200), "exception": (1380, R[7], 200),
}
PAGE_W, PAGE_H = 1640, R[7] + BH + 170

def rect(p):
    x, y, w = BOX[p]
    return x, y, w, BH

edges = [(a, b) for a, bs in DEPS.items() for b in bs]

# ---- puertos: abajo si el destino esta mas abajo, arriba si esta mas arriba ----
def side(a, b):
    return "down" if BOX[b][1] > BOX[a][1] else "up"

out_lists, in_lists = {}, {}
for a, b in edges:
    s = side(a, b)
    out_lists.setdefault((a, s), []).append(b)
    in_lists.setdefault((b, "top" if s == "down" else "bottom"), []).append(a)
cx = lambda p: BOX[p][0] + BOX[p][2] / 2
for k in out_lists: out_lists[k].sort(key=cx)
for k in in_lists: in_lists[k].sort(key=cx)

def port_x(p, i, n, avoid_tab=False):
    x, y, w = BOX[p]
    lo = x + (TAB_W + 20 if avoid_tab else 40)
    hi = x + w - 40
    lo = -(-lo // G) * G
    hi = hi // G * G
    if n == 1:
        return int(round((x + w / 2) / G)) * G
    step = max(G, ((hi - lo) // (n - 1)) // G * G)
    start = lo + ((hi - lo) - step * (n - 1)) // 2 // G * G
    return start + i * step

# ---- rejilla de obstaculos ----
NX, NY = PAGE_W // G + 1, PAGE_H // G + 1
blocked = [[False] * NY for _ in range(NX)]
for p in BOX:
    x, y, w, h = rect(p)
    extra = 120 if p in TRANSV else 0
    for gx in range((x - G) // G + 1, (x + w + G) // G):
        for gy in range((y - G) // G + 1, (y + h + extra + G) // G):
            if 0 <= gx < NX and 0 <= gy < NY:
                blocked[gx][gy] = True
near = [[False] * NY for _ in range(NX)]
for p in BOX:
    x, y, w, h = rect(p)
    for gx in range((x - 2 * G) // G, (x + w + 2 * G) // G + 1):
        for gy in range((y - 2 * G) // G, (y + h + 2 * G) // G + 1):
            if 0 <= gx < NX and 0 <= gy < NY:
                near[gx][gy] = True
for gx in range(NX):           # zona del titulo
    for gy in range(0, 100 // G):
        blocked[gx][gy] = True

use_h = {}   # (gx,gy) -> {destino: n}
use_v = {}
CUR = [None]
def other(m, c):
    return sum(v for k, v in m.get(c, {}).items() if k != CUR[0])
def same(m, c):
    return m.get(c, {}).get(CUR[0], 0) > 0
DIRS = {0: (1, 0), 1: (-1, 0), 2: (0, 1), 3: (0, -1)}

def route(start, sdir, goal, gdir):
    """A* ortogonal: start/goal en celdas libres; sdir direccion inicial, gdir direccion de llegada."""
    sx, sy = start; tx, ty = goal
    BEND, OVER, CROSS, ADJ, NEAR = 8, 80, 3, 12, 6
    pq = [(0, 0, sx, sy, sdir)]
    best = {(sx, sy, sdir): 0}
    prev = {}
    while pq:
        f, g, x, y, d = heapq.heappop(pq)
        if (x, y) == (tx, ty) and d == gdir:
            path = [(x, y)]
            k = (x, y, d)
            while k in prev:
                k = prev[k]
                path.append((k[0], k[1]))
            return path[::-1]
        if g > best.get((x, y, d), 1e18):
            continue
        for nd, (dx, dy) in DIRS.items():
            if (nd ^ 1) == d and nd // 2 == d // 2:   # no retroceder
                continue
            nx, ny = x + dx, y + dy
            if not (0 <= nx < NX and 0 <= ny < NY) or blocked[nx][ny]:
                continue
            horiz = nd < 2
            par, perp = (use_h, use_v) if horiz else (use_v, use_h)
            c = 0.3 if same(par, (nx, ny)) else 1
            c += BEND if nd != d else 0
            c += NEAR if near[nx][ny] else 0
            c += OVER * other(par, (nx, ny)) + CROSS * (other(perp, (nx, ny)) > 0)
            for off in ((0, 1), (0, -1)) if horiz else ((1, 0), (-1, 0)):
                nb = (nx + off[0], ny + off[1])
                c += ADJ * (other(par, nb) > 0)
                if same(par, nb) and not same(par, (nx, ny)):
                    c += ADJ   # mismo destino: fusionar, no correr en paralelo
            ng = g + c
            key = (nx, ny, nd)
            if ng < best.get(key, 1e18):
                best[key] = ng
                prev[key] = (x, y, d)
                h = abs(nx - tx) + abs(ny - ty)
                heapq.heappush(pq, (ng + h, ng, nx, ny, nd))
    raise RuntimeError(f"sin ruta {start}->{goal}")

def simplify(pts):
    out = [pts[0]]
    for i in range(1, len(pts) - 1):
        a, b, c = out[-1], pts[i], pts[i + 1]
        if (a[0] == b[0] == c[0]) or (a[1] == b[1] == c[1]):
            continue
        out.append(b)
    out.append(pts[-1])
    return out

# ---- asignacion de puertos: alinear salida y entrada cuando hay linea recta posible ----
def pspan(p, avoid_tab, pad=40):
    x, y, w = BOX[p]
    lo = x + (TAB_W + 20 if avoid_tab else pad)
    hi = x + w - pad
    return -(-lo // G) * G, hi // G * G

def clear_vertical(xp, y0, y1, a, b):
    for q_, (qx, qy, qw) in BOX.items():
        if q_ in (a, b):
            continue
        if qx - G <= xp <= qx + qw + G and not (qy + BH < min(y0, y1) or qy > max(y0, y1)):
            return False
    return True

PORT_OUT, ENTRY = {}, {}
used_out = {}
for (b, sd_), srcs in sorted(in_lists.items(), key=lambda kv: -len(kv[1])):
    best = None
    for a in srcs:
        down = sd_ == "top"
        alo, ahi = pspan(a, avoid_tab=not down)
        blo, bhi = pspan(b, avoid_tab=down, pad=30)
        lo, hi = max(alo, blo), min(ahi, bhi)
        if lo > hi:
            continue
        mid = (lo + hi) / 2
        for xp in sorted(range(lo, hi + 1, G), key=lambda v: abs(v - mid)):
            if any(abs(xp - u) < 2 * G for u in used_out.get(a, [])):
                continue
            ya = BOX[a][1] + (BH if down else 0)
            yb = BOX[b][1] + (0 if down else BH)
            if clear_vertical(xp, ya, yb, a, b):
                gap = abs(yb - ya)
                if best is None or gap < best[0]:
                    best = (gap, a, xp)
                break
    if best:
        _, a, xp = best
        ENTRY[(b, sd_)] = xp
        PORT_OUT[(a, b)] = xp
        used_out.setdefault(a, []).append(xp)
    else:
        blo, bhi = pspan(b, avoid_tab=(sd_ == "top"), pad=30)
        ENTRY[(b, sd_)] = int(round((blo + bhi) / 2 / G)) * G

for (a, sd_), tgts in out_lists.items():
    lo, hi = pspan(a, avoid_tab=(sd_ == "up"))
    free = [t for t in tgts if (a, t) not in PORT_OUT]
    for i, t in enumerate(free):
        ideal = lo + (hi - lo) * (i + 0.5) / len(free) if len(free) > 1 else (lo + hi) / 2
        taken = used_out.setdefault(a, [])
        for need in (2 * G, G):
            cands = [v for v in range(lo, hi + 1, G) if all(abs(v - u) >= need for u in taken)]
            if cands:
                break
        xp = min(cands, key=lambda v: abs(v - ideal))
        PORT_OUT[(a, t)] = xp
        taken.append(xp)

routes = {}
order = sorted(edges, key=lambda e: (PORT_OUT[e] != ENTRY[(e[1], "top" if side(*e) == "down" else "bottom")],
                                     e[1], -abs(BOX[e[0]][1] - BOX[e[1]][1])))
for a, b in order:
    CUR[0] = b
    s = side(a, b)
    ol = out_lists[(a, s)]
    il = in_lists[(b, "top" if s == "down" else "bottom")]
    x1 = PORT_OUT[(a, b)]
    x2 = ENTRY[(b, "top" if s == "down" else "bottom")]
    ax, ay, aw, ah = rect(a); bx, by, bw, bh = rect(b)
    if s == "down":
        p0, p1 = (x1, ay + ah), (x2, by + TAB_H)
        st, gl, sd, gd = (x1 // G, (ay + ah + G) // G), (x2 // G, (by - G) // G), 2, 2
    else:
        p0, p1 = (x1, ay + TAB_H), (x2, by + bh)
        st, gl, sd, gd = (x1 // G, (ay - G) // G), (x2 // G, (by + bh + G) // G), 3, 3
    for c in (st, gl):
        blocked[c[0]][c[1]] = False
    path = route(st, sd, gl, gd)
    for (x0, y0), (x1_, y1_) in zip(path, path[1:]):
        m = use_h if y0 == y1_ else use_v
        for c in ((x0, y0), (x1_, y1_)):
            m.setdefault(c, {})
            m[c][b] = 1
    pts = [p0] + [(x * G, y * G) for x, y in path] + [p1]
    routes[(a, b)] = simplify(pts)

# ---- enderezar quiebres cortos (bajar, correrse <= 2G, volver a bajar) ----
def seg_cells(p_, q_):
    (x0, y0), (x1, y1) = p_, q_
    if x0 == x1:
        lo, hi = sorted((y0, y1))
        return [(x0 // G, y // G) for y in range(-(-lo // G) * G, hi + 1, G)], "v"
    lo, hi = sorted((x0, x1))
    return [(x // G, y0 // G) for x in range(-(-lo // G) * G, hi + 1, G)], "h"

def inside_any_end(c, a, b):
    for q_ in (a, b):
        x, y, w, h = rect(q_)
        if x - G < c[0] * G < x + w + G and y - G < c[1] * G < y + h + G:
            return True
    return False

def free_for(p_, q_, a, b):
    cells, o = seg_cells(p_, q_)
    m = use_v if o == "v" else use_h
    for c in cells:
        if inside_any_end(c, a, b):
            continue
        if not (0 <= c[0] < NX and 0 <= c[1] < NY) or blocked[c[0]][c[1]]:
            return False
        if any(k != b for k in m.get(c, {})):
            return False
    return True

def mark(p_, q_, b):
    cells, o = seg_cells(p_, q_)
    m = use_v if o == "v" else use_h
    for c in cells:
        m.setdefault(c, {})[b] = 1

def dejog(a, b, pts):
    changed = True
    while changed:
        changed = False
        for i in range(len(pts) - 3):
            P0, P1, P2, P3 = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
            vert = P0[0] == P1[0] and P1[1] == P2[1] and P2[0] == P3[0]
            if not vert or abs(P1[0] - P2[0]) > 2 * G:
                continue
            if (P1[1] - P0[1]) * (P3[1] - P2[1]) <= 0:
                continue
            nx_ = P2[0]
            # opcion A: correr el primer tramo vertical hasta la x del segundo
            okA = False
            if i == 0:
                lo, hi = pspan(a, avoid_tab=(side(a, b) == "up"))
                others = [v for (aa, t), v in PORT_OUT.items() if aa == a and t != b]
                okA = lo <= nx_ <= hi and all(abs(nx_ - v) >= G for v in others)
            elif pts[i - 1][1] == P0[1]:
                okA = True
            if okA and free_for((nx_, P0[1]), (nx_, P1[1]), a, b):
                pts[i] = (nx_, P0[1])
                del pts[i + 1:i + 3]
                mark(pts[i], pts[i + 1], b)
                if i == 0:
                    PORT_OUT[(a, b)] = nx_
                changed = True
                break
            # opcion B: correr el segundo tramo vertical hasta la x del primero
            if i + 3 < len(pts) - 1 and pts[i + 4][1] == P3[1] if i + 4 < len(pts) else False:
                if free_for((P1[0], P2[1]), (P1[0], P3[1]), a, b):
                    pts[i + 3] = (P1[0], P3[1])
                    del pts[i + 1:i + 3]
                    mark(pts[i], pts[i + 1], b)
                    changed = True
                    break
    return simplify(pts)

for _ in range(3):
    for (a, b) in list(routes):
        routes[(a, b)] = dejog(a, b, list(routes[(a, b)]))

def jogs():
    n_ = 0
    for pts in routes.values():
        for i in range(len(pts) - 3):
            P0, P1, P2, P3 = pts[i:i + 4]
            if P0[0] == P1[0] and P1[1] == P2[1] and P2[0] == P3[0] and abs(P1[0] - P2[0]) <= 2 * G \
               and (P1[1] - P0[1]) * (P3[1] - P2[1]) > 0:
                n_ += 1
    return n_
print("quiebres cortos restantes:", jogs(), file=sys.stderr)

# ---- estilos ----
def colors(p):
    if p in TRANSV: return "#fff2cc", "#d6b656"
    if p in ("service", "service.impl"): return "#d5e8d4", "#82b366"
    return "#dae8fc", "#6c8ebf"

EDGE_COL = {"model": "#7b3fa0", "repository": "#c0571b", "security": "#1f6fb2", "service": "#2e8540",
            "bootstrap": "#a3711a", "service.support": "#b0306a", "mapper": "#127b7b", "cache": "#6b6b00"}
ecol = lambda b: EDGE_COL.get(b, "#555555")
TITLE = "Diagrama de paquetes — unisimon.demo (HorizontalSoftware-Back-End)"
NOTE = ("Flecha punteada = dependencia («import»); las que llegan al mismo paquete se unen en un tronco (mismo color). dto.request, dto.response y exception son transversales: "
        "se muestran sin flechas e indican quién los usa.")
q = {'"': "&quot;"}

# ---- draw.io ----
cells, ids = [], {p: f"pk{i}" for i, p in enumerate(PK)}
for p, (n, d) in PK.items():
    x, y, w, h = rect(p)
    f, s = colors(p)
    val = escape(f"<b>{p}</b><br><font style=\"font-size:10px\">{d}<br>{n} clase{'s' if n != 1 else ''}</font>", q)
    st = (f"shape=folder;tabWidth={TAB_W};tabHeight={TAB_H};tabPosition=left;spacingTop={TAB_H};html=1;"
          f"whiteSpace=wrap;fillColor={f};strokeColor={s};fontSize=12;")
    cells.append(f'<mxCell id="{ids[p]}" value="{val}" style="{st}" vertex="1" parent="1">'
                 f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
for i, ((a, b), pts) in enumerate(routes.items()):
    (sx, sy), (tx, ty) = pts[0], pts[-1]
    ax, ay, aw, ah = rect(a); bx, by, bw, bh = rect(b)
    pts_xml = "".join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in pts[1:-1])
    st = (f"endArrow=open;endSize=10;dashed=1;html=1;strokeColor={ecol(b)};rounded=0;edgeStyle=none;"
          f"exitX={(sx - ax) / aw:.4f};exitY={(sy - ay) / ah:.4f};exitDx=0;exitDy=0;"
          f"entryX={(tx - bx) / bw:.4f};entryY={(ty - by) / bh:.4f};entryDx=0;entryDy=0;")
    cells.append(f'<mxCell id="e{i}" value="" style="{st}" edge="1" parent="1" source="{ids[a]}" target="{ids[b]}">'
                 f'<mxGeometry relative="1" as="geometry"><Array as="points">{pts_xml}</Array></mxGeometry></mxCell>')
for p, us in TRANSV.items():
    x, y, w, h = rect(p)
    t = escape("usado por:<br>" + "<br>".join(us), q)
    cells.append(f'<mxCell id="u_{ids[p]}" value="{t}" style="text;html=1;fontSize=10;align=center;verticalAlign=top;'
                 f'fontColor=#555555;" vertex="1" parent="1"><mxGeometry x="{x}" y="{y + h + 4}" width="{w}" '
                 f'height="120" as="geometry"/></mxCell>')
cells.append(f'<mxCell id="title" value="{escape("<b>" + TITLE + "</b>", q)}" style="text;html=1;fontSize=18;'
             f'align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="20" width="1000" '
             f'height="30" as="geometry"/></mxCell>')
cells.append(f'<mxCell id="note" value="{escape(NOTE, q)}" style="text;html=1;fontSize=12;align=left;'
             f'verticalAlign=middle;whiteSpace=wrap;fontColor=#444444;" vertex="1" parent="1">'
             f'<mxGeometry x="40" y="55" width="1500" height="30" as="geometry"/></mxCell>')
xml = ('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net">\n'
       '  <diagram name="Paquetes" id="paquetes-backend">\n'
       f'    <mxGraphModel grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" '
       f'pageScale="1" pageWidth="{PAGE_W}" pageHeight="{PAGE_H}" math="0" shadow="0">\n      <root>\n'
       '        <mxCell id="0"/>\n        <mxCell id="1" parent="0"/>\n        '
       + "\n        ".join(cells) + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n")
open(os.path.join(OUT, "paquetes-backend.xml"), "w", encoding="utf-8").write(xml)

# ---- SVG ----
s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{PAGE_W}" height="{PAGE_H}" viewBox="0 0 {PAGE_W} {PAGE_H}" '
     f'font-family="Segoe UI, Helvetica, Arial, sans-serif">',
     '<defs><marker id="a" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="11" markerHeight="11" '
     'orient="auto"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#444" stroke-width="1.4"/></marker></defs>',
     f'<rect width="{PAGE_W}" height="{PAGE_H}" fill="#fff"/>',
     f'<text x="40" y="42" font-size="20" font-weight="bold" fill="#111">{escape(TITLE)}</text>',
     f'<text x="40" y="72" font-size="13" fill="#444">{escape(NOTE)}</text>']
segs, heads = {}, {}
for (a, b), pts in routes.items():
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if x0 == x1:
            for y in range(min(y0, y1), max(y0, y1), G): segs.setdefault(b, set()).add(((x0, y), (x0, y + G)))
        else:
            for x in range(min(x0, x1), max(x0, x1), G): segs.setdefault(b, set()).add(((x, y0), (x + G, y0)))
    (ex, ey), (fx, fy) = pts[-2], pts[-1]
    step = G if fy > ey else -G
    heads[(b, pts[-1])] = ((fx, fy - step), (fx, fy))
def runs(ss):
    hs = sorted([sg for sg in ss if sg[0][1] == sg[1][1]], key=lambda t: (t[0][1], t[0][0]))
    vs = sorted([sg for sg in ss if sg[0][0] == sg[1][0]], key=lambda t: (t[0][0], t[0][1]))
    out = []
    for lst in (hs, vs):
        cur = None
        for a_, b_ in lst:
            if cur and cur[1] == a_: cur = (cur[0], b_)
            else:
                if cur: out.append(cur)
                cur = (a_, b_)
        if cur: out.append(cur)
    return out
mk = []
for b in sorted(segs, key=lambda t: t in EDGE_COL):
    col = ecol(b)
    mk.append(f'<marker id="m{abs(hash(b))}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="11" markerHeight="11" '
              f'orient="auto"><path d="M0,0 L10,5 L0,10" fill="none" stroke="{col}" stroke-width="1.5"/></marker>')
    for (x0, y0), (x1, y1) in runs(segs[b]):
        s.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{col}" stroke-width="1.4" stroke-dasharray="6 4"/>')
for (b, _), ((x0, y0), (x1, y1)) in heads.items():
    s.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{ecol(b)}" stroke-width="1.4" '
             f'marker-end="url(#m{abs(hash(b))})"/>')
s.insert(1, "<defs>" + "".join(mk) + "</defs>")
for p, (n, d) in PK.items():
    x, y, w, h = rect(p)
    f, st = colors(p)
    s.append(f'<rect x="{x}" y="{y}" width="{TAB_W}" height="{TAB_H}" fill="{f}" stroke="{st}" stroke-width="1.3"/>')
    s.append(f'<rect x="{x}" y="{y + TAB_H}" width="{w}" height="{BODY_H}" fill="{f}" stroke="{st}" stroke-width="1.3"/>')
    c = x + w / 2
    s.append(f'<text x="{c}" y="{y + TAB_H + 24}" text-anchor="middle" font-size="14" font-weight="bold" fill="#111">{p}</text>')
    s.append(f'<text x="{c}" y="{y + TAB_H + 42}" text-anchor="middle" font-size="11" fill="#333">{d}</text>')
    s.append(f'<text x="{c}" y="{y + TAB_H + 58}" text-anchor="middle" font-size="11" fill="#555">{n} clase{"s" if n != 1 else ""}</text>')
for p, us in TRANSV.items():
    x, y, w, h = rect(p)
    for k, u in enumerate(["usado por:"] + us):
        s.append(f'<text x="{x + w / 2}" y="{y + h + 16 + k * 13}" text-anchor="middle" font-size="10.5" fill="#555">{u}</text>')
s.append("</svg>")
open(os.path.join(OUT, "paquetes-backend.svg"), "w", encoding="utf-8").write("\n".join(s))
print(PAGE_W, PAGE_H)
