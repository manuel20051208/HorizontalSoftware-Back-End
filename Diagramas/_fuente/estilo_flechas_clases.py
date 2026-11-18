import re, sys, html, xml.etree.ElementTree as ET
xmlp, auditp = sys.argv[1], sys.argv[2]
t = ET.parse(xmlp); root = t.getroot()
cells = {c.get('id'): c for c in root.iter('mxCell')}
def nombre(c):
    v = re.sub(r'<[^>]+>', ' ', html.unescape(c.get('value') or ''))
    v = v.replace('«Interface»', ' ').replace('«interface»', ' ')
    w = v.split(); return w[0] if w else ''
# roles desde la auditoria: arista -> [(ClaseDueña, atributo)]
roles = {}; pend = None; pair = None
for ln in open(auditp, encoding='utf-8'):
    m = re.search(r"\| (\w+) - (\w+) \|.*\| \[(.*)\]\s*$", ln)
    if m: pair = (m.group(1), m.group(2)); pend = re.findall(r"'(\w+)\.(\w+)", m.group(3)); continue
    m = re.search(r'arista (dcsh-\d+)', ln)
    if m and pend is not None: roles[m.group(1)] = (pair, pend); pend = None
BASE = 'edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;html=1;strokeColor=#000000;strokeWidth=1;jumpStyle=arc;jumpSize=8;'
LBL = 'edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];labelBackgroundColor=none;fontFamily=Courier New;fontSize=11;fontColor=#000000;'
def kv(st): return dict(p.split('=', 1) for p in st.split(';') if '=' in p)
def lado(X, Y):
    X, Y = float(X), float(Y)
    return 'arriba' if Y == 0 else 'abajo' if Y == 1 else 'izq' if X == 0 else 'der'
def off(l, txt, rol=False):
    a = 3.5 * len(txt) + 10.8
    o = {'arriba': (-a, -16), 'abajo': (a, 16), 'izq': (-a + 1, 15), 'der': (a - 1, -15)}[l]
    if rol:  # lado opuesto de la linea
        o = (-o[0], o[1]) if l in ('arriba', 'abajo') else (o[0], -o[1])
    return round(o[0], 1), round(o[1], 1)
edges = [c for c in root.iter('mxCell') if c.get('edge') == '1']
parent_of = {}
for p in root.iter():
    for ch in p: parent_of[ch] = p
for e in edges:
    s = kv(e.get('style'))
    anc = {k: s[k] for k in ('exitX', 'exitY', 'entryX', 'entryY')}
    if s.get('endArrow') == 'block':
        arrow = 'startArrow=none;endArrow=block;endFill=0;endSize=12;' + ('dashed=1;' if s.get('dashed') == '1' else '')
    elif s.get('startArrow') == 'diamondThin':
        end = 'endArrow=open;endFill=0;endSize=10;' if s.get('endArrow') == 'open' else 'endArrow=none;'
        arrow = 'startArrow=diamondThin;startFill=%s;startSize=16;' % s.get('startFill', '0') + end
    else:
        arrow = 'startArrow=none;endArrow=open;endFill=0;endSize=10;'
    e.set('style', BASE + arrow + 'exitX=%s;exitY=%s;exitDx=0;exitDy=0;entryX=%s;entryY=%s;entryDx=0;entryDy=0;' % (anc['exitX'], anc['exitY'], anc['entryX'], anc['entryY']))
    lados = {'-1': lado(anc['exitX'], anc['exitY']), '1': lado(anc['entryX'], anc['entryY'])}
    eid = e.get('id')
    labs = [c for c in root.iter('mxCell') if c.get('parent') == eid]
    for c in labs:
        if '-rol' in c.get('id'): parent_of[c].remove(c); continue
        g = c.find('mxGeometry'); x = g.get('x'); c.set('style', LBL)
        o = g.find('mxPoint'); ox, oy = off(lados[x], c.get('value'))
        o.set('x', str(ox)); o.set('y', str(oy))
    if eid in roles:
        (a, b), attrs = roles[eid]
        src, tgt = nombre(cells[e.get('source')]), nombre(cells[e.get('target')])
        prop = [d for d in attrs if d[0] == src] or attrs[:1]
        for k, (dueno, attr) in enumerate(prop):
            otra = b if dueno == a else a
            x = '-1' if otra == src else '1' if otra == tgt else None
            if x is None: print('?', eid, dueno, attr, src, tgt); continue
            ox, oy = off(lados[x], attr, rol=True)
            nc = ET.Element('mxCell', {'id': '%s-rol%d' % (eid, k), 'value': attr, 'style': LBL, 'vertex': '1', 'connectable': '0', 'parent': eid})
            g = ET.SubElement(nc, 'mxGeometry', {'x': x, 'relative': '1', 'as': 'geometry'})
            ET.SubElement(g, 'mxPoint', {'x': str(ox), 'y': str(oy), 'as': 'offset'})
            par = parent_of[e]; par.insert(list(par).index(e) + 1, nc)
# --- reparto: extremos en el mismo lado de la misma caja ---
grupos = {}
for e in edges:
    st = kv(e.get('style'))
    for x, cid, X, Y in (('-1', e.get('source'), st['exitX'], st['exitY']), ('1', e.get('target'), st['entryX'], st['entryY'])):
        l = lado(X, Y); pos = float(X) if l in ('arriba', 'abajo') else float(Y)
        labs = [c for c in root.iter('mxCell') if c.get('parent') == e.get('id') and c.find('mxGeometry').get('x') == x]
        if labs: grupos.setdefault((cid, l), []).append((pos, l, labs))
for (cid, l), ends in grupos.items():
    ends.sort(key=lambda t: t[0])
    for i, (pos, l, labs) in enumerate(ends):
        if len(ends) == 1 or 0 < i < len(ends) - 1: continue
        sgn = -1 if i == 0 else 1
        mult = [c for c in labs if '-rol' not in c.get('id')]; rol = [c for c in labs if '-rol' in c.get('id')]
        orden = mult + rol
        if l in ('arriba', 'abajo'):
            vy = -16 if l == 'arriba' else 16; acc = 10.8
            for c in orden:
                h = 3.5 * len(c.get('value')); o = c.find('mxGeometry').find('mxPoint')
                o.set('x', str(round(sgn * (acc + h), 1))); o.set('y', str(vy)); acc += 2 * h + 8
        else:
            vx = -1 if l == 'izq' else 1; acc = 15
            for c in orden:
                a = 3.5 * len(c.get('value')) + 10.8; o = c.find('mxGeometry').find('mxPoint')
                o.set('x', str(round(vx * a, 1))); o.set('y', str(sgn * acc)); acc += 14
ids = [c.get('id') for c in root.iter('mxCell')]
assert len(ids) == len(set(ids)), 'ids duplicados'
t.write(xmlp, encoding='utf-8', xml_declaration=False)
print('aristas', len(edges), 'roles', sum(1 for i in ids if '-rol' in i))
