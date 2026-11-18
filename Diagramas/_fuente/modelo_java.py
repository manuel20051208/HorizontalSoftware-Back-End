"""Parser ligero de Java para el modelo: clases, campos, metodos (firma + cuerpo) y llamadas en cada cuerpo."""
import os, re, glob

MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "java", "unisimon", "demo", "model")
KW = {"if", "for", "while", "switch", "return", "new", "catch", "synchronized", "throw", "else", "super", "this"}


def strip_comments(s):
    s = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group()), s, flags=re.S)
    s = re.sub(r"//[^\n]*", "", s)
    s = re.sub(r'"""(.*?)"""', lambda m: '"' + re.sub(r"[^\n]", " ", m.group()[2:-2]) + '"', s, flags=re.S)
    s = re.sub(r'"(\\.|[^"\\\n])*"', lambda m: '"' + " " * (len(m.group()) - 2) + '"', s)
    return s


def match_brace(s, i, o="{", c="}"):
    d = 0
    for j in range(i, len(s)):
        if s[j] == o:
            d += 1
        elif s[j] == c:
            d -= 1
            if d == 0:
                return j
    return -1


def split_top(s):
    out, d, cur = [], 0, ""
    for ch in s:
        if ch in "<([{":
            d += 1
        elif ch in ">)]}":
            d -= 1
        if ch == "," and d == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [x.strip() for x in out]


def norm_t(t):
    return re.sub(r"\s+", "", t)


class M:
    def __init__(s, **k):
        s.__dict__.update(k)

    def sig(s, ret=True):
        ps = ", ".join(f"{n} : {t}" for t, n in s.params)
        r = "" if (not ret or s.ret in (None, "void")) else f" : {s.ret}"
        return f"{s.name}({ps}){r}"

    def __repr__(s):
        return f"{s.cls}.{s.sig()}"


HDR = re.compile(r"((?:public|protected|private|static|final|abstract|\s)*)\s*([\w<>\[\],?]+(?:\s*<[^()]*>)?)?\s+(\w+)\s*\(([^()]*)\)\s*(?:throws[\w\s,]+)?$", re.S)


def parse(path=MODEL):
    classes = {}
    for f in sorted(glob.glob(os.path.join(path, "*.java"))):
        src = open(f, encoding="utf-8").read()
        s = strip_comments(src)
        m = re.search(r"\b(class|interface)\s+(\w+)(?:\s+extends\s+(\w+))?(?:\s+implements\s+([\w,\s]+))?\s*\{", s)
        cname = m.group(2)
        start = m.end() - 1
        end = match_brace(s, start)
        body = s[start + 1:end]
        cls = dict(name=cname, sup=m.group(3), fields={}, methods=[], file=f)
        pos = 0
        while pos < len(body):
            ch = body[pos]
            if ch == "{":
                hdr_start = max(body.rfind(";", 0, pos), body.rfind("}", 0, pos)) + 1
                hdr = body[hdr_start:pos].strip()
                hdr = re.sub(r"@\w+(\([^)]*\))?", " ", hdr).strip()
                e = match_brace(body, pos)
                ctor = re.match(r"((?:public|protected|private|\s)*)\s*(" + cname + r")\s*\(([^()]*)\)$", hdr, re.S)
                hm = HDR.search(hdr)
                if (ctor or hm) and not re.search(r"\b(class|interface|enum)\b", hdr):
                    if ctor:
                        mods, ret, name, ps = ctor.group(1), None, cname, ctor.group(3)
                    else:
                        mods, ret, name, ps = hm.group(1) or "", norm_t(hm.group(2) or ""), hm.group(3), hm.group(4)
                    params = []
                    for p in split_top(ps):
                        p = p.replace("final ", "").strip()
                        mm = re.match(r"(.+?)\s+(\w+)$", p, re.S)
                        if mm:
                            params.append((norm_t(mm.group(1)), mm.group(2)))
                    vis = ("private" if "private" in mods else "protected" if "protected" in mods
                           else "public" if "public" in mods else "package")
                    line = src[:start + 1 + pos].count("\n") + 1
                    cls["methods"].append(M(cls=cname, name=name, params=params, ret=ret, ctor=bool(ctor), vis=vis,
                                            static=("static" in mods), body=body[pos + 1:e], line=line))
                pos = e + 1
                continue
            pos += 1
        d = 0
        stmt = ""
        for ch in body:
            if ch == "{":
                d += 1
                stmt = ""
                continue
            if ch == "}":
                d -= 1
                stmt = ""
                continue
            if d == 0:
                if ch == ";":
                    st = re.sub(r"@\w+(\([^)]*\))?", "", stmt).strip()
                    fm = re.match(r"(?:(?:public|protected|private|static|final|transient)\s+)*([\w<>,\s\[\]?]+?)\s+(\w+)\s*(=.*)?$", st, re.S)
                    if fm and "(" not in fm.group(1):
                        cls["fields"][fm.group(2)] = norm_t(fm.group(1))
                    stmt = ""
                else:
                    stmt += ch
        classes[cname] = cls
    return classes


def hierarchy(classes, c):
    out = []
    while c in classes:
        out.append(c)
        c = classes[c]["sup"]
    return out


def find_methods(classes, c, name):
    for k in hierarchy(classes, c):
        ms = [m for m in classes[k]["methods"] if m.name == name and not m.ctor]
        if ms:
            return ms
    return []


def field_type(classes, c, f):
    for k in hierarchy(classes, c):
        if f in classes[k]["fields"]:
            return classes[k]["fields"][f]
    return None


def elem(t):
    if not t:
        return None
    m = re.match(r"(?:List|Set|Collection|Optional)<(\w+)>", t)
    return m.group(1) if m else t


def calls_in(classes, m, extra_names=()):
    """Lista ordenada de (pos, clase, metodo M | None, texto) de llamadas a operaciones del modelo en el cuerpo."""
    body = m.body
    local = {n: t for t, n in m.params}
    for mm in re.finditer(r"\b([A-Z]\w*(?:<[\w<>,\s]+>)?)\s+(\w+)\s*(?:=|:|;|\))", body):
        local.setdefault(mm.group(2), norm_t(mm.group(1)))
    for mm in re.finditer(r"instanceof\s+(\w+)\s+(\w+)", body):
        local[mm.group(2)] = mm.group(1)
    names = {mt.name for k in classes for mt in classes[k]["methods"] if not mt.ctor}
    out = []

    def vtype(tok):
        tok = tok.strip()
        if tok in ("this", ""):
            return m.cls
        if tok == "super":
            return classes[m.cls]["sup"]
        if tok in local:
            return elem(local[tok])
        ft = field_type(classes, m.cls, tok)
        if ft:
            return elem(ft)
        gm = re.match(r"get(\w+)\(\)$", tok)
        if gm:
            f = gm.group(1)[0].lower() + gm.group(1)[1:]
            for k in classes:
                if f in classes[k]["fields"]:
                    return elem(classes[k]["fields"][f])
        if tok in classes:
            return tok
        return None

    for mm in re.finditer(r"(?:(\w+(?:\(\))?)\s*\.\s*)?\b(\w+)\s*\(", body):
        recv, name = mm.group(1), mm.group(2)
        if name in KW or name not in names:
            continue
        pre = body[max(0, mm.start() - 1):mm.start()]
        if recv is None and pre in (".",):
            recv = "?"
        if re.search(r"\bnew\s+$", body[max(0, mm.start() - 5):mm.start()]):
            continue
        e = match_brace(body, mm.end() - 1, "(", ")")
        nargs = len(split_top(body[mm.end():e]))
        if recv == "?":
            t = None
        elif recv is None:
            t = m.cls
        else:
            t = vtype(recv)
        cands = []
        if t:
            cands = [x for x in find_methods(classes, t, name) if len(x.params) == nargs]
        if not cands:
            allc = [x for k in classes for x in classes[k]["methods"] if x.name == name and len(x.params) == nargs and not x.ctor]
            owners = {x.cls for x in allc}
            if len(owners) == 1:
                cands = allc
                t = t if t and allc[0].cls in hierarchy(classes, t) else allc[0].cls
        out.append((mm.start(), t, cands[0] if cands else None, re.sub(r"\s+", " ", body[mm.start():e + 1])))
    for mm in re.finditer(r"\b(\w+)::(\w+)", body):
        t = m.cls if mm.group(1) == "this" else mm.group(1)
        if t in classes:
            c = find_methods(classes, t, mm.group(2))
            if c:
                out.append((mm.start(), t, c[0], mm.group()))
    for mm in re.finditer(r"\bnew\s+(\w+)\s*\(", body):
        if mm.group(1) in classes:
            e = match_brace(body, mm.end() - 1, "(", ")")
            n = len(split_top(body[mm.end():e]))
            cs = [x for x in classes[mm.group(1)]["methods"] if x.ctor and len(x.params) == n]
            out.append((mm.start(), mm.group(1), cs[0] if cs else None, re.sub(r"\s+", " ", body[mm.start():e + 1])))
    return sorted(out, key=lambda x: x[0])


if __name__ == "__main__":
    C = parse()
    for c in C.values():
        print("##", c["name"], "extends", c["sup"], " fields:", len(c["fields"]))
        for m in c["methods"]:
            print("   %-9s%-7s %s" % (m.vis, "static" if m.static else "", ("«ctor» " if m.ctor else "") + m.sig()))
            for _, t, mt, txt in calls_in(C, m):
                print("        ->", t, mt.sig() if mt else "??", "   |", txt[:70])
