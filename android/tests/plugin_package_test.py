#!/usr/bin/env python3
"""Inspect delivered bytes: native plugin runtime must ship for the selected ABI."""
from pathlib import Path
import hashlib,json,sys,zipfile
apk=Path(sys.argv[1]);abi=sys.argv[2] if len(sys.argv)>2 else 'arm64-v8a'
with zipfile.ZipFile(apk) as z:
 runtime=f'lib/{abi}/libmagpie-bun.so'
 assert runtime in z.namelist(),f'FAIL: Plugin runtime missing for {abi}; the APK cannot provide its own install/host runtime'
 lock=json.loads((Path(__file__).resolve().parents[1]/'bun.lock.json').read_text())
 assert hashlib.sha256(z.read(runtime)).hexdigest()==lock['targets'][abi]['binary_sha256'],'FAIL: bundled runtime differs from pinned official Android Bun'
 assert f'lib/{abi}/libmagpie.so' in z.namelist(),'FAIL: core missing'
 assert not any(n.startswith('lib/') and not n.startswith(f'lib/{abi}/') for n in z.namelist()),'FAIL: unexpected ABI in architecture-specific APK'
 assert 'assets/BUN-LICENSE.md' in z.namelist(),'FAIL: runtime license missing'
print('PASS: selected ABI core, pinned Android plugin runtime and license are present in delivered APK')
