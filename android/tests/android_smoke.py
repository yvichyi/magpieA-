"""Rooted disposable emulator validation, not an app runtime root requirement."""
import pathlib, subprocess, os, re, json, threading, urllib.request, http.cookiejar, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
ROOT=pathlib.Path(__file__).resolve().parents[2]
ADB=os.environ.get('ADB','/workspace/toolchains/android-sdk/platform-tools/adb')
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
def adb(*args):return subprocess.check_output([ADB,'-s',SERIAL,*args],timeout=30)
raw=adb('shell','pidof libmagpie.so').decode().strip();pid=raw.split()[0]
env=dict(item.split(b'=',1) for item in adb('exec-out','cat',f'/proc/{pid}/environ').split(b'\0') if b'=' in item)
key=env[b'MAGPIE_WEB_KEY'].decode()
# Read only this lab app's socket ownership; never log the generated key.
links=adb('shell',f'for f in /proc/{pid}/fd/*; do readlink "$f" || :; done').decode()
inodes=set(re.findall(r'socket:\[(\d+)\]',links))
ports=[]
for name in ['tcp','tcp6']:
 for line in adb('exec-out','cat',f'/proc/{pid}/net/{name}').decode().splitlines()[1:]:
  fields=line.split()
  if len(fields)>9 and fields[3]=='0A' and fields[9] in inodes:ports.append(int(fields[1].split(':')[1],16))
management=[x for x in ports if x!=3425]
assert len(management)==1,('unexpected listener count',len(management))
port=management[0]
adb('forward','tcp:13430',f'tcp:{port}');adb('forward','tcp:13425','tcp:3425');adb('reverse','tcp:18982','tcp:18982')
class Upstream(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{"data":[{"id":"android-echo","object":"model"},{"id":"android-limited","object":"model"}],"object":"list"}')
 def do_POST(self):
  body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  self.server.calls.append(body)
  if body.get('model')=='android-limited':
   self.send_response(429);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{"error":{"message":"local fixture quota exhausted","type":"rate_limit_error"}}');return
  if body.get('stream'):
   self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
   for delta,finish in [({'role':'assistant','content':'Real Android gateway verified'},None),({},'stop')]:
    event={'id':'android-real','object':'chat.completion.chunk','model':'android-echo','choices':[{'index':0,'delta':delta,'finish_reason':finish}]}
    self.wfile.write(('data: '+json.dumps(event)+'\n\n').encode())
   usage={'id':'android-real','object':'chat.completion.chunk','model':'android-echo','choices':[],'usage':{'prompt_tokens':8,'completion_tokens':4,'total_tokens':12}}
   self.wfile.write(('data: '+json.dumps(usage)+'\n\ndata: [DONE]\n\n').encode());self.wfile.flush();return
  self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers()
  self.wfile.write(json.dumps({'id':'android-real','object':'chat.completion','model':'android-echo','choices':[{'index':0,'message':{'role':'assistant','content':'Real Android gateway verified'},'finish_reason':'stop'}],'usage':{'prompt_tokens':8,'completion_tokens':4,'total_tokens':12}}).encode())
server=ThreadingHTTPServer(('0.0.0.0',18982),Upstream);server.calls=[];threading.Thread(target=server.serve_forever,daemon=True).start()
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),urllib.request.ProxyHandler({}))
client.open('http://127.0.0.1:13430/?k='+key,timeout=45).read()
def request(path,body=None,gateway=False):
 base='http://127.0.0.1:13425' if gateway else 'http://127.0.0.1:13430'
 headers={'Content-Type':'application/json'}
 if gateway:headers['Authorization']='Bearer magpie'
 req=urllib.request.Request(base+path,None if body is None else json.dumps(body).encode(),headers)
 try:return json.loads(client.open(req,timeout=45).read())
 except urllib.error.HTTPError as e:raise RuntimeError(f'HTTP {e.code}: '+e.read().decode()[:1200]) from e
results=[]
def passed(name):results.append(name);print('PASS',name,flush=True)
request('/api/provider/save',{'id':'android-device-verified','name':'安卓设备验证','chat':'http://127.0.0.1:18982/v1','key':'emulator-local-fixture','models':['android-echo','android-limited']})
passed('Native Android core accepts provider configuration in private app storage')
models=request('/v1/models',gateway=True);assert any(x['id']=='android-device-verified/android-echo' for x in models['data']);passed('Actual Android gateway returns configured model list')
before=request('/api/usage?period=today&tz=Etc%2FUTC')
chat=request('/v1/chat/completions',{'model':'android-device-verified/android-echo','messages':[{'role':'user','content':'android device smoke'}]},True)
assert chat['choices'][0]['message']['content']=='Real Android gateway verified';assert server.calls;passed('Android core reaches real HTTP upstream and returns completion')
for _ in range(20):
 after=request('/api/usage?period=today&tz=Etc%2FUTC')
 if after['calls']==before['calls']+1:break
 time.sleep(.25)
assert after['calls']==before['calls']+1 and after['input']==before['input']+8 and after['output']==before['output']+4
passed('Actual Android usage ledger increments by exactly one call and twelve reported tokens')
route=request('/api/groups/save',{'id':'android-device-fallback','name':'安卓备用路由验证','routing':'order','members':['android-device-verified/android-limited','android-device-verified/android-echo']})
assert not route.get('error'),route
fallback=request('/v1/chat/completions',{'model':'group/android-device-fallback','messages':[{'role':'user','content':'verify actual Android fallback'}]},True)
assert fallback['choices'][0]['message']['content']=='Real Android gateway verified'
assert any(c.get('model')=='android-limited' for c in server.calls)
passed('Actual Android ordered route falls back after upstream 429 and returns the backup model')
translated=request('/v1/messages',{'model':'android-device-verified/android-echo','messages':[{'role':'user','content':'verify Android protocol conversion'}],'max_tokens':32},True)
assert translated['content'][0]['text']=='Real Android gateway verified'
passed('Actual Android Anthropic Messages converts through the configured OpenAI upstream')
r=request('/api/backup/export',{'pass':'device-fixture-passphrase','keys':True,'library':True});assert r.get('path');passed('Actual Android core creates sealed backup in app private Downloads')
import base64
backup_path=r['path'].replace('~',env[b'HOME'].decode(),1)
sealed=adb('exec-out','cat',backup_path)
payload={'data':base64.b64encode(sealed).decode(),'pass':'wrong-fixture-pass','agents':False,'library':False}
try:
 bad=request('/api/backup/import',payload)
 assert bad.get('error'),'Wrong backup password accepted'
except RuntimeError as error:
 assert 'HTTP 400' in str(error) or 'HTTP 500' in str(error)
restored=request('/api/backup/import',{**payload,'pass':'device-fixture-passphrase'})
assert 'error' not in restored,restored
assert any(x['id']=='android-device-verified/android-echo' for x in request('/v1/models',gateway=True)['data'])
assert any(x['id']=='android-device-fallback' for x in request('/api/groups')['groups'])
passed('Actual Android backup rejects a wrong password and restores providers with the correct password')
evidence=ROOT/'android/build/evidence';(evidence/'android-smoke-results.json').write_text(json.dumps({'passed':results,'managementPortIsRandom':port!=3430,'upstreamRequests':len(server.calls)},ensure_ascii=False,indent=2))
server.shutdown();print(f'{len(results)} actual Android smoke checks passed')
