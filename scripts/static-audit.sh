#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 - <<'PY'
from pathlib import Path
import json,re,sys
root=Path('.')
agent_pages=sorted((root/'docs'/'agent').rglob('*.html'))
if not agent_pages:
    raise SystemExit('FAIL no agent docs')
count=0
for p in agent_pages:
    text=p.read_text(errors='replace')
    scripts=re.findall(r'<script[^>]+id=["\']agent-index["\'][^>]*>(.*?)</script>',text,re.I|re.S)
    if p.name!='AGENT_INDEX.html' and not scripts:
        raise SystemExit(f'FAIL missing agent-index: {p}')
    if p.name=='AGENT_INDEX.html':
        continue
    for raw in scripts:
        data=json.loads(raw)
        for key in ('capability','module','intents','inputs','outputs'):
            if key not in data: raise SystemExit(f'FAIL {p}: missing {key}')
        count += 1
master=(root/'docs'/'AGENT_USER_README.html').read_text(errors='replace')
for token in ('#10100e','agent-index','prefers-reduced-motion','Hyper'):
    if token.lower() not in master.lower(): raise SystemExit(f'FAIL master doc missing {token}')
# Secret-pattern hygiene: source may name ENV variables but must not contain plausible live token literals.
all_text='\n'.join(p.read_text(errors='replace') for p in root.rglob('*') if p.is_file() and p.stat().st_size < 3_000_000 and '.git' not in p.parts)
patterns={
    'openai-token':r'\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}',
    'google-api-key':r'\bAIza[0-9A-Za-z_-]{30,}',
}
for name,pat in patterns.items():
    if re.search(pat,all_text): raise SystemExit(f'FAIL possible {name} in repository')
print(f'PASS agent HTML manifests: {count}')
print(f'PASS master hyper-index Offworld/reduced-motion markers')
print('PASS secret literal scan')
PY
# Offworld anti-pattern guard in authored UI/theme source. Brand/API names are allowed; decorative SaaS palette is not.
if grep -RniE '#007bff|border-radius:[[:space:]]*(8|10|12|14|16)px|linear-gradient\([^)]*(#7c3aed|#8b5cf6|#06b6d4)' src/main docs/AGENT_USER_README.html docs/agent >/tmp/offworld-static-audit.txt; then
  cat /tmp/offworld-static-audit.txt
  echo 'FAIL Offworld anti-pattern marker found'
  exit 1
fi
echo 'PASS Offworld anti-pattern scan'
