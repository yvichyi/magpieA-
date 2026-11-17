#!/usr/bin/env python3
"""Constrained window matrix on a disposable SDK emulator; restores display settings."""
import os,subprocess,tarfile,io
from pathlib import Path
adb=os.environ.get('ADB','adb');root=Path(__file__).resolve().parents[2]
def run(args,timeout=240):
 p=subprocess.run([adb]+args,capture_output=True,text=True,timeout=timeout)
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 return p.stdout.strip()
if not run(['get-serialno']).startswith('emulator-'):raise RuntimeError('Disposable SDK emulator required')
evidence=root/'android/verification/1.10';evidence.mkdir(parents=True,exist_ok=True)
old_size=run(['shell','wm','size']);old_font=run(['shell','settings','get','system','font_scale'])
try:
 for name,size,font in [('compact','320x568','2.0'),('normal','394x852','1.0'),('large-font','394x852','1.5'),('landscape','640x360','1.5'),('short','320x360','2.0'),('resize','640x640','2.0')]:
  run(['shell','wm','size',size]);run(['shell','settings','put','system','font_scale',font])
  out=run(['shell','am','instrument','-w','-r','-e','mode','resize' if name=='resize' else 'flow','ai.magpie.android.tests/ai.magpie.android.NativeResponsiveAcceptance'],480)
  (evidence/('native-'+name+'.log')).write_text(out);print(name+'\n'+out,flush=True)
  assert 'INSTRUMENTATION_CODE: -1' in out,'Responsive acceptance failed '+name
  dest=root/'android/design/1.10'/name;dest.mkdir(parents=True,exist_ok=True)
  p=subprocess.run([adb,'exec-out','tar','-c','-C','/data/user/0/ai.magpie.android/files/responsive-acceptance','.'],capture_output=True,timeout=60)
  assert p.returncode==0,p.stderr
  with tarfile.open(fileobj=io.BytesIO(p.stdout)) as z:
   for m in z:
    if m.isfile() and m.name.endswith('.png') and m.size<8*1024*1024:(dest/Path(m.name).name).write_bytes(z.extractfile(m).read())
finally:
 override=[line.split(': ',1)[1] for line in old_size.splitlines() if line.startswith('Override size:')]
 run(['shell','wm','size',override[0] if override else 'reset']);run(['shell','settings',*(['delete','system','font_scale'] if old_font=='null' else ['put','system','font_scale',old_font])])
print('PASS native width/font/short-window matrix')
