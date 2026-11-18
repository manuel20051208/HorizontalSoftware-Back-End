"""Audita cada arista del diagrama de clases contra la NORMA (skill diagramas-uml, sec. 2) y el modelo + Flyway.
Deriva del codigo: herencias, realizaciones y asociaciones (tipo, todo, multiplicidades, navegabilidad) y compara."""
import sys, re, glob, os, html, xml.etree.ElementTree as ET
REPO, XML = sys.argv[1], sys.argv[2]
MODEL = os.path.join(REPO, 'src/main/java/unisimon/demo/model')
MIG = sorted(glob.glob(os.path.join(REPO, 'src/main/resources/db/migration/V*.sql')),
             key=lambda p: int(re.search(r'V(\d+)__', p).group(1)))


def nocom(s):
    return re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', s, flags=re.S))


# ---------- modelo ----------
src = {os.path.basename(f)[:-5]: nocom(open(f, encoding='utf-8').read()) for f in glob.glob(MODEL + '/*.java')}
CLASES = set(src)
herencia, realiza, tabla = {}, set(), {}
for c, s in src.items():
    m = re.search(r'\b(?:class|interface)\s+' + c + r'\s+(?:extends\s+(\w+))?\s*(?:implements\s+([\w,\s]+))?\{', s)
    if m and m.group(1):
        herencia[c] = m.group(1)
    if m and m.group(2):
        for i in m.group(2).split(','):
            realiza.add((c, i.strip()))
    t = re.search(r'@Table\(name\s*=\s*"(\w+)"', s)
    if t:
        tabla[c] = t.group(1)
for c in CLASES:
    k = c
    while k not in tabla and k in herencia:
        k = herencia[k]
    if k in tabla:
        tabla[c] = tabla[k]

campos = []
PAT = r'((?:@\w+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)private\s+(?:final\s+)?([\w<>, ]+?)\s+(\w+)\s*(?:=[^;]*)?;'
for c, s in src.items():
    for m in re.finditer(PAT, s):
        anot, tipo, nombre = m.group(1), m.group(2).replace(' ', ''), m.group(3)
        mm = re.match(r'List<(\w+)>$', tipo)
        dest = mm.group(1) if mm else tipo
        if dest not in CLASES:
            continue
        campos.append(dict(clase=c, nombre=nombre, dest=dest, col=bool(mm), anot=' '.join(anot.split()),
                           transient='@Transient' in anot))

# ---------- Flyway: estado final de cada FK ----------
fk = {}
for p in MIG:
    sql = re.sub(r'--[^\n]*', '', open(p, encoding='utf-8').read())
    for tm in re.finditer(r'CREATE TABLE (\w+)\s*\((.*?)\n\);', sql, re.S):
        for ln in tm.group(2).split('\n'):
            cm = re.match(r'\s*(\w+)\s+\w+.*?REFERENCES\s+(\w+)', ln)
            if cm:
                od = re.search(r'ON DELETE (CASCADE|SET NULL|NO ACTION)', ln)
                fk[(tm.group(1), cm.group(1))] = dict(ref=cm.group(2), notnull='NOT NULL' in ln,
                                                      ondelete=od.group(1) if od else 'NO ACTION')
    for am in re.finditer(r"ALTER TABLE (\w+)\s+ADD CONSTRAINT \w+\s+FOREIGN KEY \((\w+)\) REFERENCES (\w+) \(id\)"
                          r"(?: ON DELETE (CASCADE|SET NULL|NO ACTION))?", sql):
        prev = fk.get((am.group(1), am.group(2)), dict(notnull=False))
        fk[(am.group(1), am.group(2))] = dict(ref=am.group(3), notnull=prev['notnull'], ondelete=am.group(4) or 'NO ACTION')
    for fm in re.finditer(r"fijar_borrado\('(\w+)',\s*'(\w+)',\s*'(\w+)',\s*'([A-Z ]+)'", sql):
        fk[(fm.group(1), fm.group(2))]['ondelete'] = fm.group(4)
    for nm in re.finditer(r'ALTER TABLE (\w+) ALTER COLUMN (\w+) SET NOT NULL', sql):
        fk[(nm.group(1), nm.group(2))]['notnull'] = True


def joincol(f):
    m = re.search(r'@JoinColumn\(name\s*=\s*"(\w+)"', f['anot'])
    return m.group(1) if m else None


def mapped(f):
    m = re.search(r'mappedBy\s*=\s*"(\w+)"', f['anot'])
    return m.group(1) if m else None


def target(f):
    m = re.search(r'targetEntity\s*=\s*(\w+)\.class', f['anot'])
    return m.group(1) if m else f['dest']


def cascada(f):
    return bool(re.search(r'CascadeType\.(ALL|REMOVE)|orphanRemoval\s*=\s*true', f['anot']))


# ---------- agrupar atributos en asociaciones (una arista por asociacion) ----------
usados, asocs = set(), []
for i, f in enumerate(campos):
    if i in usados:
        continue
    grupo = [i]
    mb = mapped(f)
    for j, g in enumerate(campos):
        if j == i or j in usados:
            continue
        if mb and g['clase'] == target(f) and g['nombre'] == mb:
            grupo.append(j)
        elif mapped(g) and target(g) == f['clase'] and mapped(g) == f['nombre']:
            grupo.append(j)
        elif (not mb and not mapped(g) and g['clase'] != f['clase']
              and {g['clase'], g['dest']} == {f['clase'], f['dest']}):
            grupo.append(j)  # referencias cruzadas sin mappedBy (Inmueble.liquidacion / Liquidacion.inmueble)
    usados.update(grupo)
    asocs.append([campos[k] for k in grupo])


def esperado(g):
    lista = [f for f in g if f['col']]
    if lista:
        a, b = lista[0]['clase'], lista[0]['dest']
    else:
        a, b = g[0]['clase'], g[0]['dest']

    def extremo(clase):  # Residente.inmueble cuenta en el extremo Usuario si la lista es List<Usuario>
        if clase in (a, b):
            return clase
        k = clase
        while k in herencia:
            k = herencia[k]
            if k in (a, b):
                return k
        return clase

    info = None
    for f in g:
        jc = joincol(f)
        if jc and (tabla.get(f['clase']), jc) in fk:
            info = (f, fk[(tabla[f['clase']], jc)])
    m2m = any('@ManyToMany' in f['anot'] for f in g)
    tipo, todo = 'asociacion', None
    casc = [f for f in g if cascada(f)]
    if casc:
        tipo, todo = 'composicion', casc[0]['clase']
    elif info and info[1]['ondelete'] == 'CASCADE':
        tipo, todo = 'composicion', info[0]['dest']
    elif lista:
        tipo, todo = 'agregacion', lista[0]['clase']
    todo = extremo(todo) if todo else None
    parte = (b if todo == a else a) if todo else None
    mult, fuente = {}, {}
    for f in g:
        d = extremo(f['dest'])
        if f['col']:
            mult[d], fuente[d] = '0..*', f"List en {f['clase']}.{f['nombre']}"
        elif not joincol(f):
            mult[d], fuente[d] = '0..1', f"referencia unica sin FK {f['clase']}.{f['nombre']}"
    fks = [(f, fk[(tabla[f['clase']], joincol(f))]) for f in g if joincol(f) and (tabla.get(f['clase']), joincol(f)) in fk]
    for f, dfk in fks:  # extremo destino de cada FK: NOT NULL -> 1, nullable -> 0..1
        d = extremo(f['dest'])
        mult[d] = '1' if dfk['notnull'] else '0..1'
        fuente[d] = f"FK {tabla[f['clase']]}.{joincol(f)} {'NOT NULL' if dfk['notnull'] else 'nullable'}"
    for f, dfk in fks:  # extremo duenno de la FK, si nada mas lo fijo
        own = extremo(f['clase'])
        if own not in mult:
            mult[own] = '0..*' if '@ManyToOne' in f['anot'] else '0..1'
            fuente[own] = '@ManyToOne' if '@ManyToOne' in f['anot'] else '@OneToOne unique'
    if m2m:
        for x in (a, b):
            if x not in mult:
                mult[x], fuente[x] = '0..*', '@ManyToMany (tabla intermedia)'
    nav = {extremo(f['dest']) for f in g}
    return dict(tipo=tipo, todo=todo, parte=parte, a=a, b=b, mult=mult, fuente=fuente, nav=nav,
                attrs=[f"{f['clase']}.{f['nombre']}{' (@Transient)' if f['transient'] else ''}" for f in g])


esp = [esperado(g) for g in asocs]

# ---------- diagrama ----------
t = ET.parse(XML)
cells = list(t.iter('mxCell'))
byid = {c.get('id'): c for c in cells}


def nombre(i):
    v = byid[i].get('value') or ''
    m = re.search(r'<b>(\w+)</b>', v)
    return m.group(1) if m else re.sub('<[^>]+>', '', html.unescape(v)).strip()


def sty(s):
    return dict(kv.split('=', 1) for kv in (s or '').split(';') if '=' in kv)


aristas = []
for c in cells:
    if c.get('edge') != '1':
        continue
    lbl = {}
    for k in cells:
        if k.get('parent') == c.get('id'):
            x = float(k.find('mxGeometry').get('x', 0))
            lbl.setdefault('s' if x < 0 else 't', []).append(k.get('value'))
    aristas.append(dict(id=c.get('id'), src=nombre(c.get('source')), dst=nombre(c.get('target')),
                        st=sty(c.get('style')), lbl=lbl))


def tipo_arista(e):
    s = e['st']
    ini = s.get('startArrow', 'none').startswith('diamond')
    fin = s.get('endArrow', '').startswith('diamond')
    if s.get('endArrow') == 'block' and s.get('endFill') == '0':
        return 'realizacion' if s.get('dashed') == '1' else 'herencia'
    if ini or fin:
        return 'composicion' if s.get('startFill' if ini else 'endFill', '1') == '1' else 'agregacion'
    return 'asociacion'


problemas = 0


def mal(e, msg):
    global problemas
    problemas += 1
    print(f"      [X] {e['id']} ({e['src']} -> {e['dst']}): {msg}")


restantes = list(aristas)
print('== herencias / realizaciones ==')
for par, tp in [(p, 'herencia') for p in sorted(herencia.items())] + [(p, 'realizacion') for p in sorted(realiza)]:
    e = next((e for e in restantes if tipo_arista(e) == tp and (e['src'], e['dst']) == par), None)
    print(f"  {tp:<11} {par[0]} -> {par[1]}: {'OK ' + e['id'] if e else 'FALTA'}")
    if e:
        restantes.remove(e)
        if e['st'].get('startArrow', 'none') != 'none':
            mal(e, 'startArrow en una generalizacion')
        if e['lbl']:
            mal(e, 'multiplicidad en una generalizacion')
    else:
        problemas += 1

print('== asociaciones (todo -> parte) ==')
for x in esp:
    e = next((e for e in restantes if tipo_arista(e) not in ('herencia', 'realizacion')
              and {e['src'], e['dst']} == {x['a'], x['b']}), None)
    print(f"  {x['tipo']:<11} todo={x['todo'] or '-'} | {x['a']} - {x['b']} | mult={x['mult']} "
          f"| nav->{sorted(x['nav'])} | {x['attrs']}")
    for k, v in x['fuente'].items():
        print(f"        {k}: {x['mult'][k]} por {v}")
    if not e:
        print('      [X] FALTA arista')
        problemas += 1
        continue
    restantes.remove(e)
    s = e['st']
    print(f"      arista {e['id']}: {e['src']} -> {e['dst']} start={s.get('startArrow', 'none')}/{s.get('startFill', '-')} "
          f"end={s.get('endArrow', '-')}/{s.get('endFill', '-')} lbl={e['lbl']}")
    if tipo_arista(e) != x['tipo']:
        mal(e, f"tipo {tipo_arista(e)} -> debe ser {x['tipo']}")
    if x['todo']:
        if e['src'] != x['todo']:
            mal(e, f"source debe ser el todo ({x['todo']})")
        if not s.get('startArrow', '').startswith('diamond'):
            mal(e, 'el rombo debe ser el startArrow (extremo del todo)')
        if s.get('endArrow', 'none').startswith('diamond'):
            mal(e, 'rombo en la parte (endArrow): PROHIBIDO')
        want = 'open' if x['parte'] in x['nav'] else 'none'
        if s.get('endArrow', 'classic') != want:
            mal(e, f"endArrow={s.get('endArrow')} -> {want} (navegable: {sorted(x['nav'])})")
    else:
        if s.get('startArrow', 'none') != 'none':
            mal(e, f"startArrow={s.get('startArrow')} en asociacion simple")
        if len(x['nav']) == 1:
            dest = next(iter(x['nav']))
            if e['dst'] != dest:
                mal(e, f"la punta navegable debe ir hacia {dest}")
            if s.get('endArrow') != 'open':
                mal(e, f"endArrow={s.get('endArrow')} -> open")
    lado = {e['src']: 's', e['dst']: 't'}
    for clase, m in x['mult'].items():
        got = e['lbl'].get(lado.get(clase, '?'), [])
        if got != [m]:
            mal(e, f"multiplicidad junto a {clase}: {got} -> {m}")
    for k, v in e['lbl'].items():
        if len(v) > 1:
            mal(e, f'etiquetas duplicadas en el extremo {k}: {v}')
for e in restantes:
    mal(e, 'SOBRA: ningun atributo ni herencia del modelo la respalda')
print(f'{len(aristas)} aristas en el XML; PROBLEMAS: {problemas}')
