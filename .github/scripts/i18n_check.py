#!/usr/bin/env python3
"""Revisa los archivos de idioma de assets/i18n.

Uso: i18n_check.py [carpeta del repositorio]

Falla (exit 1) si un archivo tiene claves repetidas, claves que ya no existen en
en.txt o marcadores distintos a los del español ({} / {1} / %s / %d / \\n / {z}/{x}/{y}).
Las traducciones que faltan solo se avisan: las añade quien traduce y mientras
tanto la app muestra el texto en español.
"""
import os
import re
import sys
from collections import Counter

BASE = 'app/src/main/assets/i18n'
MARKERS = re.compile(r'\{\d*\}|%[sd]|\\n|\{[xyz]\}')


def load(path):
    pairs, dup = {}, []
    for n, line in enumerate(open(path, encoding='utf-8'), 1):
        s = line.rstrip('\r\n')
        if '\t' in s and not s.lstrip().startswith('#'):
            k, v = s.split('\t', 1)
            if k in pairs:
                dup.append((n, k))
            pairs[k] = v
    return pairs, dup


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    folder = os.path.join(root, BASE)
    en, _ = load(os.path.join(folder, 'en.txt'))
    errors, summary = [], []

    for name in sorted(os.listdir(folder)):
        if not name.endswith('.txt') or name == 'en.txt':
            continue
        rel = '%s/%s' % (BASE, name)
        tr, dup = load(os.path.join(folder, name))
        for line, key in dup:
            errors.append((rel, line, 'clave repetida: %s' % key))
        for key in tr:
            if key not in en:
                errors.append((rel, None, 'clave que ya no existe en en.txt: %s' % key))
            elif Counter(MARKERS.findall(en[key])) != Counter(MARKERS.findall(tr[key])):
                errors.append((rel, None, 'marcadores distintos: %s' % key))
        missing = len(set(en) - set(tr))
        summary.append((name, len(tr), len(en), missing))
        if missing:
            print('::warning file=%s::%d textos sin traducir (%d/%d)' % (rel, missing, len(tr), len(en)))

    print('\n| archivo | claves | faltan |')
    print('| --- | ---: | ---: |')
    for name, keys, total, missing in summary:
        print('| %s | %d/%d | %d |' % (name, keys, total, missing))
    print()

    step = os.environ.get('GITHUB_STEP_SUMMARY')
    if step:
        with open(step, 'a', encoding='utf-8') as f:
            f.write('## Traducciones\n\n| archivo | claves | faltan |\n| --- | ---: | ---: |\n')
            for name, keys, total, missing in summary:
                f.write('| %s | %d/%d | %d |\n' % (name, keys, total, missing))

    for rel, line, msg in errors:
        where = '%s,line=%d' % (rel, line) if line else rel
        print('::error file=%s::%s' % (where, msg))
        print('%s: %s' % (rel, msg))
    print('%d errores' % len(errors))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
