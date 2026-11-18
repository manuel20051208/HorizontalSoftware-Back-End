"""Audita clase por clase: atributos y operaciones del diagrama vs. el modelo Java (firma por firma)."""
import sys, re, glob, os, html, xml.etree.ElementTree as ET
REPO = sys.argv[1]; XML = sys.argv[2]
MODEL = os.path.join(REPO, 'src/main/java/unisimon/demo/model')
VIS = {'public': '+', 'private': '-', 'protected': '#', '': '~'}

def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    return re.sub(r'//[^\n]*', '', s)

def split_params(p):
    out, depth, cur = [], 0, ''
    for ch in p:
        if ch == '<': depth += 1
        if ch == '>': depth -= 1
        if ch == ',' and depth == 0: out.append(cur); cur = ''
        else: cur += ch
    if cur.strip(): out.append(cur)
    return out

def java_members(path):
    src = strip_comments(open(path, encoding='utf-8').read())
    cls = os.path.basename(path)[:-5]
    # keep only depth-1 text of the top-level class body (drops method bodies and nested classes)
    start = re.search(r'\b(class|interface)\s+' + cls + r'\b[^{]*\{', src).end()
    depth, buf, i = 1, '', start
    while i < len(src) and depth > 0:
        ch = src[i]
        if ch == '{':
            depth += 1
            if depth == 2: buf += '{}\n'
        elif ch == '}':
            depth -= 1
        elif depth == 1:
            buf += ch
        i += 1
    buf = re.sub(r'@\w+(\([^()]*(\([^()]*\)[^()]*)*\))?', ' ', buf)   # annotations
    attrs, ops = set(), set()
    for stmt in re.split(r';|\{\}', buf):
        stmt = ' '.join(stmt.split())
        if not stmt: continue
        m = re.match(r'^((?:(?:public|private|protected|static|final|abstract|transient)\s+)*)(.*)$', stmt)
        mods, rest = m.group(1).split(), m.group(2)
        vis = VIS[next((x for x in mods if x in VIS), '')]
        st = 'static' in mods
        if '(' in rest.split('=')[0]:
            mm = re.match(r'^(?:(.+?)\s+)?(\w+)\s*\((.*)\)\s*(throws .*)?$', rest)
            if not mm: print('??', cls, stmt); continue
            ret, name, params = mm.group(1), mm.group(2), mm.group(3)
            if name == 'class' or rest.startswith('class '): continue
            ps = []
            for p in split_params(params):
                p = p.replace('final ', '').strip()
                t, n = p.rsplit(' ', 1); ps.append((n, t.replace(' ', '')))
            ret = None if ret in (None, 'void') else ret.replace(' ', '')
            ops.add((vis, st, name, tuple(ps), ret))
        else:
            if rest.startswith('class ') or rest.startswith('interface '): continue
            decl = rest.split('=')[0].strip()
            t, n = decl.rsplit(' ', 1)
            attrs.add((vis, st, n, t.replace(' ', '')))
    return attrs, ops

def diagram_classes(xmlp):
    t = ET.parse(xmlp); cells = list(t.iter('mxCell'))
    boxes = {}
    for c in cells:
        if (c.get('style') or '').startswith('swimlane'):
            kids = [k for k in cells if k.get('parent') == c.get('id') and (k.get('style') or '').startswith('text')]
            kids.sort(key=lambda k: float(k.find('mxGeometry').get('y', 0)))
            boxes[c.get('value')] = [k.get('value') or '' for k in kids]
    return boxes

def lines(v):
    out = []
    for ln in re.split(r'<br\s*/?>', v):
        st = '<u>' in ln
        ln = html.unescape(re.sub(r'<[^>]+>', '', ln)).strip()
        if ln: out.append((st, ln))
    return out

def parse_attr(st, ln):
    m = re.match(r'^([+\-#~])\s*(\w+)\s*:\s*(.+)$', ln)
    return (m.group(1), st, m.group(2), m.group(3).replace(' ', ''))

def parse_op(st, ln):
    m = re.match(r'^([+\-#~])\s*(\w+)\s*\((.*)\)\s*(?::\s*(.+))?$', ln)
    ps = []
    for p in split_params(m.group(3)):
        n, t = p.split(':', 1); ps.append((n.strip(), t.replace(' ', '')))
    return (m.group(1), st, m.group(2), tuple(ps), m.group(4).replace(' ', '') if m.group(4) else None)

boxes = diagram_classes(XML)
total_bad = 0
for f in sorted(glob.glob(MODEL + '/*.java')):
    cls = os.path.basename(f)[:-5]
    if cls not in boxes: print(f'{cls}: (sin caja, se omite)'); continue
    ja, jo = java_members(f)
    attrs_v, ops_v = boxes[cls][0], boxes[cls][1] if len(boxes[cls]) > 1 else ''
    da = {parse_attr(*x) for x in lines(attrs_v)}
    do = {parse_op(*x) for x in lines(ops_v)}
    bad = [('FALTA atributo', x) for x in ja - da] + [('SOBRA atributo', x) for x in da - ja] + \
          [('FALTA operacion', x) for x in jo - do] + [('SOBRA operacion', x) for x in do - jo]
    total_bad += len(bad)
    print(f'{cls}: {len(ja)} atributos, {len(jo)} operaciones -> {"OK" if not bad else str(len(bad))+" diferencias"}')
    for b in sorted(bad, key=str): print('   ', *b)
print('TOTAL diferencias:', total_bad)
