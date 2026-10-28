#!/usr/bin/env python3
"""Checks that docs/MIXINS.md has a row for every mixin Fabricated's mixin configs list.

docs/MIXINS.md is kept by hand: it has a table per mixin config, under a heading naming the config's path. This reads
every mixin config under mc/ (each version's and shared folder's src/main/resources, and the test mods' resources),
takes each listed mixin (the config's package plus each entry of its "mixins", "client" and "server" arrays) and looks
for it in the Mixin column of that config's table. Rows name a mixin by its config entry (client.FooMixin,
fluid.EntityMixin) or by its simple name (FooMixin); either counts, the class's full name too, and nothing else does: a
row naming fluid.EntityMixin is no row for EntityMixin.

The doc covers every config these patterns find, the test mods' included (the deliberately broken one too), so none is
skipped; Gradle's copies under build/ don't match them. A branch without some platform's folder checks what mc/ has.

Usage, from anywhere: python3 -I scripts/check-mixin-inventory.py. Prints MISSING <config> <mixin> for each mixin
without a row and exits 1, else prints one OK line and exits 0.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / 'docs' / 'MIXINS.md'
CONFIG_PATTERNS = [f'mc/{base}/{where}' for base in ('*/*/src', '*/src')
                   for where in ('main/resources/*.mixins.json', 'testmod*/resources/**/*.mixins.json')]


def doc_tables(text):
    """Maps each config path the doc has a table for to the names in that table's Mixin column."""
    tables, current = {}, None
    for line in text.splitlines():
        heading = re.match(r'#{2,3}\s+(?:`([^`]+)`)?', line)
        if heading:
            current = heading.group(1)
            if current:
                tables.setdefault(current, set())
        elif current and line.startswith('|'):
            cells = [cell.strip().strip('`') for cell in line.strip().strip('|').split('|')]
            if len(cells) > 1:
                tables[current].add(cells[1])
    return tables


def main():
    tables = doc_tables(DOC.read_text(encoding='utf-8') if DOC.is_file() else '')
    configs = sorted({path for pattern in CONFIG_PATTERNS for path in ROOT.glob(pattern)})
    missing, checked = [], 0
    for path in configs:
        config = path.relative_to(ROOT).as_posix()
        data = json.loads(path.read_text(encoding='utf-8'))
        rows = tables.get(config, set())
        for section in ('mixins', 'client', 'server'):
            for entry in data.get(section, []):
                checked += 1
                name = f"{data['package']}.{entry}"
                if not {name, entry, entry.rsplit('.', 1)[-1]} & rows:
                    missing.append(f'MISSING {config} {name}')
    print('\n'.join(missing) if missing else
          f'OK: all {checked} mixins of {len(configs)} configs have a row in {DOC.relative_to(ROOT).as_posix()}')
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())
