#!/usr/bin/env python3
"""Run the native workflow against a local HTTP fixture on a disposable SDK emulator.
Build/install the application and instrumentation APK first. This edits lab app settings.
"""
import argparse,json,os,subprocess,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer

parser=argparse.ArgumentParser()
parser.add_argument('--mode',choices=['flow','focus','layout','keyboard'],default='flow')
args=parser.parse_args()
adb=os.environ.get('ADB','adb')
def command(*parts,timeout=60):
    p=subprocess.run([adb,*parts],capture_output=True,text=True,timeout=timeout)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout.strip()
if not command('get-serialno').startswith('emulator-'):
    raise RuntimeError('This fixture is restricted to a disposable SDK emulator')

class Fixture(BaseHTTPRequestHandler):
    def log_message(self,*unused):pass
    def reply(self,body,mime='application/json'):
        if not isinstance(body,bytes):body=json.dumps(body).encode()
        self.send_response(200);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_GET(self):self.reply({'data':[{'id':'echo'},{'id':'limited'}]})
    def do_POST(self):
        data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
        if data.get('stream'):
            frames=[]
            for delta,finish in [({'role':'assistant','content':'Native setup gateway reply'},None),({},'stop')]:
                frames.append('data: '+json.dumps({'id':'native-qa','object':'chat.completion.chunk','model':'echo','choices':[{'index':0,'delta':delta,'finish_reason':finish}]})+'\n\n')
            frames.append('data: '+json.dumps({'id':'native-qa','object':'chat.completion.chunk','model':'echo','choices':[],'usage':{'prompt_tokens':8,'completion_tokens':4,'total_tokens':12}})+'\n\ndata: [DONE]\n\n')
            self.reply(''.join(frames).encode(),'text/event-stream');return
        self.reply({'id':'native-qa','object':'chat.completion','model':'echo','choices':[{'index':0,'message':{'role':'assistant','content':'Native setup gateway reply'},'finish_reason':'stop'}],'usage':{'prompt_tokens':8,'completion_tokens':4,'total_tokens':12}})

server=ThreadingHTTPServer(('127.0.0.1',0),Fixture)
port=str(server.server_address[1]);threading.Thread(target=server.serve_forever,daemon=True).start()
old_font=command('shell','settings','get','system','font_scale')
old_ime=command('shell','settings','get','secure','show_ime_with_hard_keyboard')
try:
    command('reverse','tcp:'+port,'tcp:'+port)
    extra=[]
    if args.mode=='focus':extra=['-e','probeFocusOnly','true']
    if args.mode=='layout':extra=['-e','probeFormLayoutOnly','true']
    if args.mode=='keyboard':
        command('shell','settings','put','system','font_scale','1.5')
        command('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
        extra=['-e','formOnly','true']
    output=command('shell','am','instrument','-w','-r','-e','upstream','http://127.0.0.1:'+port+'/v1',*extra,'ai.magpie.android.tests/ai.magpie.android.NativeSetupAcceptance',timeout=480)
    print(output)
    if 'INSTRUMENTATION_CODE: -1' not in output:raise RuntimeError('Native workflow failed; preserve the output above')
finally:
    for group,key,value in [('system','font_scale',old_font),('secure','show_ime_with_hard_keyboard',old_ime)]:
        command('shell','settings',*(['delete',group,key] if value=='null' else ['put',group,key,value]))
    command('reverse','--remove','tcp:'+port)
    subprocess.run([adb,'shell','am','stopservice','-n','ai.magpie.android/.GatewayService'],capture_output=True,timeout=60)
    server.shutdown();server.server_close()
