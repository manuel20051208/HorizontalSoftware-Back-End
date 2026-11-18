"""Uso: python Diagramas/_fuente/mapa_servicios_dominio.py

Mapa: que operaciones del modelo invoca de verdad cada metodo de service/impl (directo o via sus metodos
privados, mappers, policies y otros servicios inyectados). Busca x.operacion( y resuelve el tipo de x."""
import os, re, glob, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from modelo_java import parse, strip_comments, match_brace, split_top, norm_t, find_methods, elem

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "java", "unisimon", "demo")
C = parse()
DOMAIN_NAMES = {m.name for c in C.values() for m in c["methods"] if not m.ctor}
DOMAIN_NAMES |= set()  # (getters/setters de Lombok quedan fuera a proposito)

MHDR = re.compile(r"(?:public|protected|private|static|final|synchronized|\s)*\s*(?:<[^>]+>\s*)?([\w<>\[\],.?\s]+?)\s+(\w+)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)\s*(?:throws[\w\s,.]+)?$", re.S)


def methods_of(path):
    s = strip_comments(open(path, encoding="utf-8").read())
    cm = re.search(r"\b(class|interface|record|enum)\s+(\w+)[^{]*\{", s)
    if not cm:
        return None, {}, {}
    cname = cm.group(2)
    start = cm.end() - 1
    end = match_brace(s, start)
    body = s[start + 1:end]
    fields = {}
    for fm in re.finditer(r"private\s+(?:final\s+)?([\w<>.,\s]+?)\s+(\w+)\s*;", body):
        fields[fm.group(2)] = norm_t(fm.group(1))
    meths = {}
    pos = 0
    while pos < len(body):
        if body[pos] == "{":
            hs = max(body.rfind(";", 0, pos), body.rfind("}", 0, pos)) + 1
            hdr = re.sub(r"@\w+(\([^)]*\))?", " ", body[hs:pos]).strip()
            e = match_brace(body, pos)
            hm = MHDR.search(hdr)
            if hm and not re.search(r"\b(class|interface|record|enum|new)\b", hdr) and hm.group(2) not in ("if", "for", "while", "switch", "catch"):
                params = {}
                for p in split_top(hm.group(3)):
                    p = re.sub(r"@\w+(\([^)]*\))?\s*", "", p).replace("final ", "").strip()
                    mm = re.match(r"(.+?)\s+(\w+)$", p, re.S)
                    if mm:
                        params[mm.group(2)] = norm_t(mm.group(1))
                meths.setdefault(hm.group(2), []).append(dict(params=params, body=body[pos + 1:e], ret=norm_t(hm.group(1))))
            pos = e + 1
            continue
        pos += 1
    return cname, fields, meths


FILES = {}
for f in glob.glob(os.path.join(BASE, "**", "*.java"), recursive=True):
    if os.sep + "model" + os.sep in f or "/model/" in f.replace("\\", "/"):
        continue
    n, fl, ms = methods_of(f)
    if n:
        FILES[n] = dict(path=f, fields=fl, methods=ms)

# tipo de retorno de los metodos estaticos de Hidratador y similares
def static_ret(cls, meth):
    info = FILES.get(cls)
    if not info or meth not in info["methods"]:
        return None
    return info["methods"][meth][0]["ret"]


def domain_calls(cls, mname, seen=None, depth=0):
    """Llamadas a operaciones del modelo hechas por cls.mname (y por lo que este llama dentro de la app)."""
    seen = seen or set()
    key = (cls, mname)
    if key in seen or depth > 4:
        return []
    seen.add(key)
    info = FILES.get(cls)
    if not info or mname not in info["methods"]:
        return []
    out = []
    for m in info["methods"][mname]:
        body = m["body"]
        local = dict(m["params"])
        for mm in re.finditer(r"\b([A-Z][\w.]*(?:<[\w<>,.\s]+>)?)\s+(\w+)\s*(?:=|:)", body):
            local.setdefault(mm.group(2), norm_t(mm.group(1)))
        for mm in re.finditer(r"instanceof\s+(\w+)\s+(\w+)", body):
            local[mm.group(2)] = mm.group(1)
        # var x = Hidratador.metodo(...)
        for mm in re.finditer(r"\bvar\s+(\w+)\s*=\s*(\w+)\.(\w+)\(", body):
            r = static_ret(mm.group(2), mm.group(3))
            if r:
                local[mm.group(1)] = r
        for mm in re.finditer(r"(?:([\w]+(?:\([^()]*\))?)\s*\.\s*)?\b(\w+)\s*\(", body):
            recv, name = mm.group(1), mm.group(2)
            if name in ("if", "for", "while", "switch", "catch", "return", "new"):
                continue
            rtype = None
            if recv is None and body[max(0, mm.start() - 1):mm.start()] == "." and body[mm.start() - 2] == ")":
                # receptor = llamada encadenada: Clase.metodo(args).operacion(
                j = mm.start() - 2
                d = 0
                while j >= 0:
                    if body[j] == ")":
                        d += 1
                    elif body[j] == "(":
                        d -= 1
                        if d == 0:
                            break
                    j -= 1
                q = re.search(r"(?:(\w+)\.)?(\w+)\s*$", body[:j])
                if q:
                    rtype = static_ret(q.group(1), q.group(2)) if q.group(1) else None
                    if q.group(1) is None and q.group(2) in info["methods"]:
                        rtype = info["methods"][q.group(2)][0]["ret"]
                    recv = (q.group(2) + "()") if rtype else None
                    if rtype:
                        rtype = elem(rtype.split(".")[-1])
            elif recv:
                base = recv.split("(")[0]
                if "(" in recv:  # Hidratador.invitado(v).autorizacionVigente()  -> recv = invitado(v)
                    pre = body[:mm.start()]
                    q = re.search(r"(\w+)\.$", pre)
                    rtype = static_ret(q.group(1), base) if q else None
                elif base in local:
                    rtype = elem(local[base].split(".")[-1])
                elif base in info["fields"]:
                    rtype = info["fields"][base]
                elif base in C or base in FILES:
                    rtype = base
            if name in DOMAIN_NAMES and (rtype in C or rtype is None) and not (recv and recv in FILES):
                owners = sorted({k for k in C for x in C[k]["methods"] if x.name == name and not x.ctor})
                if rtype in C:
                    ms = find_methods(C, rtype, name)
                    if ms:
                        out.append((f"{cls}.{mname}", rtype, name, re.sub(r"\s+", " ", body[mm.start():mm.start() + 80])))
                        continue
                if rtype is None:
                    # app method with the same name? (local private method / static helper)
                    if not recv and name in info["methods"]:
                        pass
                    else:
                        out.append((f"{cls}.{mname}", "?" + "/".join(owners), name, re.sub(r"\s+", " ", body[mm.start():mm.start() + 80])))
                        continue
            # recursion into app code
            if not recv and name in info["methods"] and name != mname:
                out += domain_calls(cls, name, seen, depth + 1)
            elif recv:
                base = recv.split("(")[0]
                target = None
                if base in FILES:
                    target = base
                elif base in info["fields"]:
                    t = info["fields"][base]
                    target = t + "Impl" if t + "Impl" in FILES else (t if t in FILES else None)
                if target and name in FILES[target]["methods"]:
                    out += domain_calls(target, name, seen, depth + 1)
        for mm in re.finditer(r"\bnew\s+(\w+)\s*\(", body):
            if mm.group(1) in C:
                out.append((f"{cls}.{mname}", mm.group(1), "«create» " + mm.group(1), re.sub(r"\s+", " ", body[mm.start():mm.start() + 80])))
        for mm in re.finditer(r"\b(\w+)::(\w+)", body):
            if mm.group(1) in C and mm.group(2) in DOMAIN_NAMES:
                out.append((f"{cls}.{mname}", mm.group(1), mm.group(2), mm.group()))
    return out


if __name__ == "__main__":
    for cls in sorted(k for k in FILES if k.endswith("ServiceImpl")):
        for mname in FILES[cls]["methods"]:
            body = FILES[cls]["methods"][mname][0]["body"]
            if not re.search(r"@Override", open(FILES[cls]["path"], encoding="utf-8").read()):
                pass
            calls = domain_calls(cls, mname)
            print(f"{cls}.{mname}")
            seen = set()
            for via, t, n, txt in calls:
                k = (t, n, via)
                if k in seen:
                    continue
                seen.add(k)
                print(f"      {t}.{n}" + ("" if via == f"{cls}.{mname}" else f"   [via {via}]") + f"    | {txt[:60]}")
