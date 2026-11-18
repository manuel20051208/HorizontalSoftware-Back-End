"""Uso: python Diagramas/_fuente/verificar_secuencia_codigo.py [secuencia.xml]

Compara cada pagina de secuencia-casos-de-uso.xml con el codigo del modelo.

A. firma: cada mensaje (y etiqueta de llamada a si mismo) = operacion real de la clase destino, mismos
   parametros (nombre : Tipo) y, si la etiqueta lo trae, mismo retorno.
B. retornos: la respuesta de una operacion void va sin etiqueta; la de una con retorno lleva su tipo/valor.
C. llamadas internas: lo que sale de la ejecucion de Q en el diagrama = lo que el cuerpo de Q llama en el codigo.
"""
import sys, re, html, json
import xml.etree.ElementTree as ET
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from modelo_java import parse, find_methods, calls_in, norm_t, hierarchy

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "secuencia-casos-de-uso.xml")
C = parse()


def plain(v):
    v = v or ""
    v = re.sub(r"<br\s*/?>|</?div>", " ", v)
    v = re.sub(r"<[^>]+>", "", v)
    v = html.unescape(v).replace("\xa0", " ")
    return re.sub(r"\s+", " ", v).strip()


def st(s):
    d = {}
    for p in (s or "").split(";"):
        if "=" in p:
            k, v = p.split("=", 1)
            d[k] = v
        elif p:
            d[p] = ""
    return d


def geo(c):
    g = c.find("mxGeometry")
    return [float(g.get(k) or 0) for k in ("x", "y", "width", "height")]


LBL = re.compile(r"^(«create»\s*)?(\w+)\s*\((.*)\)\s*(?::\s*(.+))?$")


def parse_label(t):
    t = re.sub(r"\(\s+", "(", t)
    m = LBL.match(t)
    if not m:
        return None
    ps = []
    if m.group(3).strip():
        for p in re.split(r",\s*(?![^<]*>)", m.group(3)):
            pm = re.match(r"(\w+)\s*:\s*(.+)$", p.strip())
            ps.append((norm_t(pm.group(2)), pm.group(1)) if pm else ("?", p.strip()))
    return m.group(2), ps, (norm_t(m.group(4)) if m.group(4) else None)


def lifeline_class(lbl):
    m = re.match(r"^(\w*)\s*:\s*(\w+)$", lbl)
    return (m.group(1), m.group(2)) if m else (None, None)


def analyze(path):
    root = ET.parse(path).getroot()
    report = {}
    used = set()
    for d in root.iter("diagram"):
        name = d.get("name")
        cells = [c for c in d.find("mxGraphModel/root") if c.tag == "mxCell"]
        lif, bars, texts = {}, {}, []
        for c in cells:
            s = st(c.get("style"))
            if s.get("shape") == "umlLifeline":
                lbl = plain(c.get("value"))
                lif[c.get("id")] = dict(label=lbl, actor="umlActor" in (c.get("style") or ""), geo=geo(c),
                                        cls=lifeline_class(lbl)[1])
        for c in cells:
            s = st(c.get("style"))
            if c.get("vertex") == "1" and "orthogonalPerimeter" in s.get("perimeter", ""):
                x, y, w, h = geo(c)
                cx = x + w / 2
                lid = min(lif, key=lambda k: abs(lif[k]["geo"][0] + lif[k]["geo"][2] / 2 - cx))
                bars[c.get("id")] = dict(lif=lid, geo=(x, y, w, h), op=None, parent=None)
            elif c.get("vertex") == "1" and s.get("shape") not in ("umlLifeline", "umlFrame"):
                v = plain(c.get("value"))
                if v and not v.startswith("[") and c.get("id") != "titulo":
                    texts.append(dict(id=c.get("id"), geo=geo(c), text=v))
        edges = []
        for c in cells:
            s = st(c.get("style"))
            if c.get("edge") != "1" or s.get("endArrow") == "none":
                continue
            pts = {p.get("as"): p for p in c.find("mxGeometry").findall("mxPoint")}
            y = float(pts["sourcePoint"].get("y")) if "sourcePoint" in pts else 0
            edges.append(dict(id=c.get("id"), src=c.get("source"), tgt=c.get("target"), y=y,
                              ret=s.get("dashed") == "1", label=plain(c.get("value"))))
        edges.sort(key=lambda e: e["y"])
        owner = lambda i: i if i in lif else bars.get(i, {}).get("lif")
        # enclosing bar
        for bid, b in bars.items():
            x, y, w, h = b["geo"]
            enc = [(ob["geo"][3], oid) for oid, ob in bars.items() if oid != bid and ob["lif"] == b["lif"]
                   and ob["geo"][1] <= y and ob["geo"][1] + ob["geo"][3] >= y + h and ob["geo"][3] > h]
            b["parent"] = min(enc)[1] if enc else None
        problems = []
        calls = []
        for e in edges:
            if e["ret"] and not e["label"].startswith("«create»"):
                continue
            selfc = e["src"] == e["tgt"]
            label = e["label"]
            tid = None
            if selfc:
                ex = [t for t in texts if abs(t["geo"][1] - (e["y"] - 2)) <= 4]
                if not ex:
                    problems.append(f"llamada a si mismo {e['id']} sin etiqueta")
                    continue
                tid = ex[0]["id"]
                label = ex[0]["text"]
            pl = parse_label(label.replace("«create»", "").strip())
            tl = owner(e["tgt"])
            cls = lif[tl]["cls"] if tl else None
            call = dict(edge=e, label=label, cls=cls, tl=tl, self=selfc, text_id=tid, method=None)
            # caller bar
            if selfc:
                call["caller_bar"] = bars[e["src"]]["parent"]
            else:
                call["caller_bar"] = e["src"] if e["src"] in bars else None
            if e["tgt"] in bars and "op_call" not in bars[e["tgt"]]:
                bars[e["tgt"]]["op_call"] = call
            elif e["tgt"] in bars and not selfc:
                problems.append(f"[D] '{label}' llega a una barra que ya es de otra ejecucion (falta su barra anidada)")
            if not pl:
                problems.append(f"[A] etiqueta no parseable: '{label}'")
                calls.append(call)
                continue
            nm, ps, rt = pl
            if cls is None:
                problems.append(f"[A] destino sin clase para '{label}'")
                calls.append(call)
                continue
            if nm == cls:
                cands = [m for m in C[cls]["methods"] if m.ctor]
            else:
                cands = find_methods(C, cls, nm)
            exact = [m for m in cands if [(t, n) for t, n in m.params] == ps]
            if not cands:
                problems.append(f"[A] {cls} no tiene la operacion '{nm}' (mensaje '{label}')")
            elif not exact:
                problems.append(f"[A] firma distinta: '{label}' -> en el codigo: " + " | ".join(m.sig() for m in cands))
            else:
                m = exact[0]
                call["method"] = m
                used.add(id(m))
                if rt is not None and norm_t(rt) != norm_t(m.ret or "void"):
                    problems.append(f"[A] retorno distinto en '{label}': codigo {m.ret}")
                if rt is None and selfc and m.ret not in (None, "void"):
                    problems.append(f"[A] etiqueta de llamada a si mismo sin retorno: '{label}' (codigo : {m.ret})")
            calls.append(call)
        # B: returns
        for e in edges:
            if not e["ret"] or e["label"].startswith("«create»"):
                continue
            sb = e["src"]
            b = bars.get(sb)
            call = b.get("op_call") if b else None
            if not call or not call["method"]:
                continue
            m = call["method"]
            lab = e["label"]
            if m.ret in (None, "void"):
                if lab and not m.ctor:
                    problems.append(f"[B] retorno de {m.name} (void) con etiqueta '{lab}'")
            else:
                if not lab:
                    problems.append(f"[B] retorno de {m.name} sin etiqueta (codigo : {m.ret})")
                elif lab in ("true", "false", "null"):
                    pass
                elif norm_t(lab) != norm_t(m.ret) and not lab.startswith(m.name + "()"):
                    problems.append(f"[B] retorno de {m.name}: '{lab}' (codigo : {m.ret})")
        # C: internal calls
        children = {}
        for call in calls:
            children.setdefault(call["caller_bar"], []).append(call)
        for bid, b in bars.items():
            oc = b.get("op_call")
            if not oc:
                continue
            q = oc["method"]
            if not q:
                continue
            diag = children.get(bid, [])
            dset = {id(c["method"]): c for c in diag if c["method"]}
            code = calls_in(C, q)
            cset = {}
            for _, t, mt, txt in code:
                if mt:
                    cset.setdefault(id(mt), (mt, txt))
            dseq = [c["method"] for c in sorted(diag, key=lambda c: c["edge"]["y"]) if c["method"]]
            cseq = [mt for _, t, mt, txt in code if mt]
            if [id(x) for x in dseq] != [id(x) for x in cseq] and dseq:
                problems.append(f"[E] orden/multiplicidad en {q.cls}.{q.name}: diagrama {[x.name for x in dseq]} vs codigo {[x.name for x in cseq]}")
            for k, c in dset.items():
                if k not in cset:
                    problems.append(f"[C] en {q.cls}.{q.name}: el diagrama llama {c['method']} y el codigo NO")
            if diag or True:
                for k, (mt, txt) in cset.items():
                    if k not in dset:
                        problems.append(f"[C] en {q.cls}.{q.name}: el codigo llama {mt}  ({txt[:50]}) y el diagrama NO"
                                        + ("" if diag else "  [ejecucion sin expandir]"))
        entries = [c for c in calls if c["caller_bar"] is None or (c["edge"]["src"] in lif and lif[c["edge"]["src"]]["actor"])]
        report[name] = dict(problems=problems, entries=[(c["cls"], c["label"]) for c in entries],
                            lifelines=[v["label"] for v in lif.values()])
    return report, used


if __name__ == "__main__":
    rep, used = analyze(SRC)
    tot = 0
    for name, r in rep.items():
        print("===", name, " | ", ", ".join(r["lifelines"]))
        for cls, lab in r["entries"]:
            print("    entrada:", cls, lab)
        for p in r["problems"]:
            print("   ", p)
        tot += len(r["problems"])
    print("TOTAL problemas:", tot)
    print("\n### Operaciones del modelo que no aparecen en ningun diagrama:")
    for c in C.values():
        for m in c["methods"]:
            if id(m) not in used:
                print("   ", ("«ctor» " if m.ctor else "") + f"{m.cls}.{m.sig()}", "", m.vis, "static" if m.static else "")
