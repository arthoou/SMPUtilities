# Builds assets/smputilities/lang/*.json.
#   tools/lang/*.tsv             key<TAB>English<TAB>Portuguese (every key lives here)
#   tools/lang/locales/<code>.lang  key=text for the other languages (French, German, ...)
# A key missing from a language falls back to English, in the game and on the server.
# Regional variants get their language's text (the game does not fall back from fr_ca to fr_fr).
# Usage: python3 tools/genlang.py tools/lang mod/src/main/resources/assets/smputilities/lang
import json, os, re, sys, collections

src = sys.argv[1]
out = sys.argv[2]
VARIANTS = {
    'pt_br': ['pt_pt'],
    'fr_fr': ['fr_ca'],
    'de_de': ['de_at', 'de_ch'],
    'es_es': ['es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve'],
}
FORMAT = re.compile(r'%(?:\d+\$)?[sd]')

en = collections.OrderedDict()
pt = collections.OrderedDict()
for name in sorted(os.listdir(src)):
    if not name.endswith('.tsv'):
        continue
    for number, line in enumerate(open(os.path.join(src, name), encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line.strip():
            continue
        parts = line.split('\t')
        if len(parts) != 3:
            raise SystemExit(f'{name}:{number}: expected 3 columns, got {len(parts)}')
        key, e, p = parts
        if key in en:
            raise SystemExit(f'{name}:{number}: duplicate key {key}')
        en[key] = e.replace('\\n', '\n')
        pt[key] = p.replace('\\n', '\n')

tables = {'en_us': en, 'pt_br': pt}
errors = []
locales = os.path.join(src, 'locales')
for name in sorted(os.listdir(locales)) if os.path.isdir(locales) else []:
    if not name.endswith('.lang'):
        continue
    code = name[:-len('.lang')]
    table = collections.OrderedDict()
    for number, line in enumerate(open(os.path.join(locales, name), encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        key, sep, text = line.partition('=')
        key = key.strip()
        if not sep:
            errors.append(f'{name}:{number}: expected key=text')
        elif key not in en:
            errors.append(f'{name}:{number}: unknown key {key}')
        elif key in table:
            errors.append(f'{name}:{number}: duplicate key {key}')
        elif sorted(FORMAT.findall(text)) != sorted(FORMAT.findall(en[key].replace('\n', '\\n'))):
            errors.append(f'{name}:{number}: {key} has {FORMAT.findall(text)}, English has {FORMAT.findall(en[key])}')
        else:
            table[key] = text.strip().replace('\\n', '\n')
    missing = [key for key in en if key not in table]
    if missing:
        print(f'{code}: {len(missing)} keys missing (English is shown), e.g. {missing[:5]}')
    tables[code] = table
if errors:
    raise SystemExit('\n'.join(errors))

for code in list(tables):
    for variant in VARIANTS.get(code, []):
        tables[variant] = tables[code]
os.makedirs(out, exist_ok=True)
for code, table in sorted(tables.items()):
    with open(os.path.join(out, code + '.json'), 'w', encoding='utf-8') as f:
        json.dump(dict(sorted(table.items())), f, ensure_ascii=False, indent=2)
        f.write('\n')
print(len(en), 'keys,', len(tables), 'languages')
