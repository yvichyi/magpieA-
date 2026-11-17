#!/usr/bin/env python3
"""Fetch the pinned official Android runtime; verify archive and executable."""
from pathlib import Path
import hashlib,json,os,shutil,sys,urllib.request,zipfile
lock=json.loads((Path(__file__).parent/'bun.lock.json').read_text());abi=sys.argv[1];dest=Path(sys.argv[2]).resolve();target=lock['targets'][abi]
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for part in iter(lambda:f.read(1048576),b''):h.update(part)
 return h.hexdigest()
dest.parent.mkdir(parents=True,exist_ok=True)
if dest.is_file() and digest(dest)==target['binary_sha256']:print('Verified pinned Bun '+lock['version']+' '+abi);sys.exit(0)
archive=dest.parent/(target['target']+'.zip');partial=archive.with_suffix('.zip.part')
try:
 if not archive.is_file() or digest(archive)!=target['zip_sha256']:
  with urllib.request.urlopen(lock['release']+'/'+archive.name,timeout=60) as src,partial.open('wb') as out:
   total=0
   while part:=src.read(1048576):
    total+=len(part)
    if total>200*1048576:raise ValueError('Runtime archive exceeds size limit')
    out.write(part)
  if digest(partial)!=target['zip_sha256']:raise ValueError('Official runtime ZIP checksum mismatch')
  partial.replace(archive)
 with zipfile.ZipFile(archive) as z:
  entry=z.getinfo(target['target']+'/bun')
  if entry.file_size>300*1048576:raise ValueError('Runtime executable exceeds size limit')
  tmp=dest.with_suffix('.part')
  with z.open(entry) as src,tmp.open('wb') as out:shutil.copyfileobj(src,out,1048576)
  if digest(tmp)!=target['binary_sha256']:tmp.unlink();raise ValueError('Runtime executable checksum mismatch')
  tmp.chmod(0o755);tmp.replace(dest)
 print('Verified official Android Bun '+lock['version']+' '+abi)
finally:
 if partial.exists():partial.unlink()
