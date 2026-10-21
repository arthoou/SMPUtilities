# Builds assets/smputilities/lang/{en_us,pt_br}.json from tools/lang/*.tsv (key<TAB>English<TAB>Portuguese).
# Usage: python3 tools/genlang.py tools/lang platforms/<target>/src/main/resources/assets/smputilities/lang
import json, os, sys, collections
src = sys.argv[1]
out = sys.argv[2]
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
os.makedirs(out, exist_ok=True)
for lang, table in (('en_us', en), ('pt_br', pt)):
    with open(os.path.join(out, lang + '.json'), 'w', encoding='utf-8') as f:
        json.dump(dict(sorted(table.items())), f, ensure_ascii=False, indent=2)
        f.write('\n')
print(len(en), 'keys')
