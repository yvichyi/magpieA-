#!/usr/bin/env python3
"""Native 1.9 acceptance against local HTTP; restricted to disposable SDK emulators."""
import argparse,subprocess,os,json,threading,tarfile,io
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
p=argparse.ArgumentParser();p.add_argument('--large-font',action='store_true');p.add_argument('--evidence',type=Path);args=p.parse_args();adb=os.environ.get('ADB','adb')
def run(parts,timeout=60):
 x=subprocess.run([adb]+parts,capture_output=True,text=True,timeout=timeout)
 if x.returncode:raise RuntimeError(x.stdout+x.stderr)
 return x.stdout.strip()
if not run(['get-serialno']).startswith('emulator-'):raise RuntimeError('Disposable SDK emulator required')
release=threading.Event();slow_seen=threading.Event()
class Fixture(BaseHTTPRequestHandler):
 def log_message(self,*unused):pass
 def send(self,value,status=200,mime='application/json'):
  if not isinstance(value,bytes):value=json.dumps(value).encode()
  self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(value)));self.end_headers();self.wfile.write(value)
 def do_GET(self):
  if self.path=='/_native19/release':release.set();self.send({'released':True});return
  if self.path=='/_native19/events':self.send({'slow':slow_seen.is_set()});return
  self.send({'data':[{'id':'echo'},{'id':'slow'},{'id':'limited'}]})
 def do_POST(self):
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}');model=body.get('model','echo')
  if model=='limited':self.send({'error':{'message':'fixture quota exhausted'}},429);return
  if model=='slow':slow_seen.set();release.wait(15)
  if body.get('stream'):
   pieces=[]
   for delta,finish in [({'role':'assistant','content':'OK'},None),({},'stop')]:pieces.append('data: '+json.dumps({'id':'native19','object':'chat.completion.chunk','model':model,'choices':[{'index':0,'delta':delta,'finish_reason':finish}]})+'\n\n')
   pieces.append('data: '+json.dumps({'id':'native19','object':'chat.completion.chunk','model':model,'choices':[],'usage':{'prompt_tokens':4,'completion_tokens':2,'total_tokens':6}})+'\n\ndata: [DONE]\n\n');self.send(''.join(pieces).encode(),mime='text/event-stream');return
  self.send({'id':'native19','object':'chat.completion','model':model,'choices':[{'index':0,'message':{'role':'assistant','content':'OK'},'finish_reason':'stop'}],'usage':{'prompt_tokens':4,'completion_tokens':2,'total_tokens':6}})
server=ThreadingHTTPServer(('127.0.0.1',0),Fixture);port=str(server.server_address[1]);threading.Thread(target=server.serve_forever,daemon=True).start()
old_font=run(['shell','settings','get','system','font_scale']);old_ime=run(['shell','settings','get','secure','show_ime_with_hard_keyboard'])
try:
 run(['reverse','tcp:'+port,'tcp:'+port]);run(['shell','settings','put','system','font_scale','1.5' if args.large_font else '1.0'])
 if args.large_font:run(['shell','settings','put','secure','show_ime_with_hard_keyboard','1'])
 output=run(['shell','am','instrument','-w','-r','-e','upstream','http://127.0.0.1:'+port+'/v1','ai.magpie.android.tests/ai.magpie.android.NativeRefineAcceptance'],600);print(output,flush=True)
 if 'INSTRUMENTATION_CODE: -1' not in output:raise RuntimeError('Native 1.9 acceptance failed')
finally:
 if args.evidence:
  args.evidence.mkdir(parents=True,exist_ok=True)
  raw=subprocess.run([adb,'exec-out','tar','-c','-C','/data/user/0/ai.magpie.android/files/native-refine-acceptance','.'],capture_output=True,timeout=60)
  if raw.returncode==0:
   with tarfile.open(fileobj=io.BytesIO(raw.stdout)) as z:
    for m in z:
     if m.isfile() and m.name.endswith('.png') and m.size<8*1024*1024:(args.evidence/Path(m.name).name).write_bytes(z.extractfile(m).read())
 for group,key,value in [('system','font_scale',old_font),('secure','show_ime_with_hard_keyboard',old_ime)]:run(['shell','settings',*(['delete',group,key] if value=='null' else ['put',group,key,value])])
 run(['reverse','--remove','tcp:'+port]);subprocess.run([adb,'shell','am','stopservice','-n','ai.magpie.android/.GatewayService'],capture_output=True,timeout=60)
 release.set();server.shutdown();server.server_close()
