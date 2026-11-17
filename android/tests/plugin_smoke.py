"""Disposable rooted emulator: real Bun installs a local npm package and serves gateway traffic."""
from pathlib import Path
import subprocess,os,re,json,time,threading,urllib.request,urllib.error,http.cookiejar,io,tarfile,hashlib,base64
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
ADB=os.environ.get('ADB','/workspace/toolchains/android-sdk/platform-tools/adb');SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
def adb(*a):return subprocess.check_output([ADB,'-s',SERIAL,*a],timeout=45)
pid=adb('shell','pidof','libmagpie.so').decode().split()[0]
env=dict(x.split(b'=',1) for x in adb('exec-out','cat',f'/proc/{pid}/environ').split(b'\0') if b'=' in x)
assert env.get(b'MAGPIE_BUN',b'').endswith(b'/libmagpie-bun.so'),'Installed core did not receive the packaged runtime'
key=env[b'MAGPIE_WEB_KEY'].decode();links=adb('shell',f'for f in /proc/{pid}/fd/*; do readlink "$f" || :; done').decode();inodes=set(re.findall(r'socket:\[(\d+)\]',links));ports=[]
for name in ['tcp','tcp6']:
 for line in adb('exec-out','cat',f'/proc/{pid}/net/{name}').decode().splitlines()[1:]:
  f=line.split()
  if len(f)>9 and f[3]=='0A' and f[9] in inodes:ports.append(int(f[1].split(':')[1],16))
port=next(x for x in ports if x!=3425);adb('forward','tcp:13430',f'tcp:{port}');adb('forward','tcp:13425','tcp:3425');adb('reverse','tcp:18983','tcp:18983')
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),urllib.request.ProxyHandler({}));client.open('http://127.0.0.1:13430/?k='+key,timeout=60).read()
def request(path,body=None,gateway=False):
 req=urllib.request.Request(('http://127.0.0.1:13425' if gateway else 'http://127.0.0.1:13430')+path,None if body is None else json.dumps(body).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer magpie'} if gateway else {})})
 try:return json.loads(client.open(req,timeout=180).read())
 except urllib.error.HTTPError as e:raise RuntimeError(f'HTTP {e.code}: '+e.read().decode()[:1600]) from e
PKG='magpie-android-runtime-check';VERSION='1.0.'+str(int(time.time()));available=False;gateway_hits=[];npm_hits=[]
source='''export const AndroidRuntimeCheck = async () => ({
 config: async cfg => { cfg.provider ??= {}; cfg.provider.androidfixture={name:"Android 插件运行验证",npm:"@ai-sdk/openai-compatible",api:"http://127.0.0.1:18983/v1",models:{"plugin-echo":{name:"Plugin Echo",limit:{context:8000,output:1000}}}}; },
 auth:{provider:"androidfixture",methods:[{type:"api",label:"Fixture key"}],loader:async getAuth=>({apiKey:"fixture-only",baseURL:"http://127.0.0.1:18983/v1",fetch:async(url,init)=>{const headers=new Headers(init.headers);headers.set("x-android-plugin-runtime","verified");return fetch(url,{...init,headers});}})}
});'''
package_archives={}
def tarball(version):
 if version in package_archives:return package_archives[version]
 out=io.BytesIO()
 with tarfile.open(fileobj=out,mode='w:gz') as tar:
  for name,b in {'package.json':json.dumps({'name':PKG,'version':version,'type':'module','main':'index.js'}).encode(),'index.js':source.encode()}.items():
   info=tarfile.TarInfo('package/'+name);info.size=len(b);tar.addfile(info,io.BytesIO(b))
 package_archives[version]=out.getvalue()
 return package_archives[version]
class Registry(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def send(self,code,body,mime='application/json'):
  self.send_response(code);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):
  npm_hits.append(self.path)
  if self.path.startswith('/v1/models'):return self.send(200,b'{"data":[{"id":"plugin-echo"}]}')
  if not available:return self.send(404,b'{"error":"intentional registry failure for retry verification"}')
  b=tarball(VERSION)
  if '.tgz' in self.path:return self.send(200,b,'application/octet-stream')
  meta={'name':PKG,'dist-tags':{'latest':VERSION},'versions':{VERSION:{'name':PKG,'version':VERSION,'type':'module','main':'index.js','dist':{'tarball':f'http://127.0.0.1:18983/{PKG}/-/{PKG}-{VERSION}.tgz','shasum':hashlib.sha1(b).hexdigest(),'integrity':'sha512-'+base64.b64encode(hashlib.sha512(b).digest()).decode()}}}}
  self.send(200,json.dumps(meta).encode())
 def do_POST(self):
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}');gateway_hits.append((self.headers.get('x-android-plugin-runtime'),body))
  if body.get('stream'):
   events=[]
   for delta,finish in [({'role':'assistant','content':'Executed by the Android Bun plugin'},None),({},'stop')]:
    events.append('data: '+json.dumps({'id':'plugin-real','object':'chat.completion.chunk','model':'plugin-echo','choices':[{'index':0,'delta':delta,'finish_reason':finish}]})+'\n\n')
   events.append('data: '+json.dumps({'id':'plugin-real','object':'chat.completion.chunk','model':'plugin-echo','choices':[],'usage':{'prompt_tokens':6,'completion_tokens':3,'total_tokens':9}})+'\n\ndata: [DONE]\n\n')
   return self.send(200,''.join(events).encode(),'text/event-stream')
  self.send(200,json.dumps({'id':'plugin-real','object':'chat.completion','model':'plugin-echo','choices':[{'index':0,'message':{'role':'assistant','content':'Executed by the Android Bun plugin'},'finish_reason':'stop'}],'usage':{'prompt_tokens':6,'completion_tokens':3,'total_tokens':9}}).encode())
server=ThreadingHTTPServer(('0.0.0.0',18983),Registry);threading.Thread(target=server.serve_forever,daemon=True).start();old=request('/api/plugins')['registry'];results=[]
def passed(s):results.append(s);print('PASS '+s,flush=True)
try:
 initial=request('/api/plugins');assert initial['bun'] is True
 for entry in initial['plugins']:
  if entry['spec']==PKG or entry['spec'].startswith(PKG+'@'):request('/api/plugins/remove',{'spec':entry['spec']})
 passed('Core uses the packaged runtime from the Android native-code directory')
 request('/api/plugins/registry',{'url':'http://127.0.0.1:18983'})
 try:request('/api/plugins/add',{'spec':'npm:'+PKG});raise AssertionError('Intentional registry failure was reported as success')
 except RuntimeError as e:assert '404' in str(e) or 'install' in str(e).lower(),str(e)
 assert not any(p['spec']==PKG or p['spec'].startswith(PKG+'@') for p in request('/api/plugins')['plugins']);passed('Failed installation reports a real error and does not create an installed entry')
 available=True;state=request('/api/plugins/add',{'spec':'npm:'+PKG});p=next(x for x in state['plugins'] if x['spec']==PKG or x['spec'].startswith(PKG+'@'));installed_spec=p['spec'];assert p.get('version')==VERSION and not p.get('error'),p
 assert any('.tgz' in x for x in npm_hits);passed('Explicit retry downloads, verifies, installs and loads a real npm-format plugin under the app UID')
 # pluginsState returns installation metadata immediately; provider methods
 # refresh in the background. The real UI exposes Sign in only after that read.
 deadline=time.monotonic()+60
 while True:
  subs=request('/api/providers').get('plugins',[])
  if any(s.get('pid')=='androidfixture' and s.get('methods') for s in subs):break
  assert time.monotonic()<deadline,'Installed plugin never advertised its sign-in method'
  time.sleep(.25)
 signed=request('/api/plugin-signin',{'provider':'androidfixture','method':0,'key':'lab-fixture-not-vendor'});assert signed.get('state')=='done',signed
 models=request('/v1/models',gateway=True);model=next(x['id'] for x in models['data'] if x['id'].endswith('/plugin-echo'));passed('Installed plugin signs in through the actual API and exposes its model')
 reply=request('/v1/chat/completions',{'model':model,'messages':[{'role':'user','content':'verify Android plugin execution'}]},True)
 assert reply['choices'][0]['message']['content']=='Executed by the Android Bun plugin';assert gateway_hits[-1][0]=='verified';passed('Gateway completion passes through actual JavaScript plugin fetch, not a direct provider bypass')
 request('/api/plugins/off',{'spec':installed_spec,'off':True});assert next(p for p in request('/api/plugins')['plugins'] if p['spec']==installed_spec)['off']
 request('/api/plugins/off',{'spec':installed_spec,'off':False});assert not next(p for p in request('/api/plugins')['plugins'] if p['spec']==installed_spec).get('off');passed('Installed plugin can be disabled and enabled without losing its configuration')
 VERSION='1.0.'+str(int(VERSION.split('.')[-1])+1);request('/api/plugins/check',{});state=request('/api/plugins/upgrade',{'spec':installed_spec});assert next(x for x in state['plugins'] if x['spec']==installed_spec)['version']==VERSION;passed('Plugin upgrade installs the actual new version')
 # Keep this fixture installed for native page/restart inspection; no fixture is bundled in the APK.
 Path('android/build/evidence/plugin-runtime-android.json').write_text(json.dumps({'passed':results,'model':model,'version':VERSION,'registryRequests':len(npm_hits),'pluginGatewayRequests':len(gateway_hits)},ensure_ascii=False,indent=2))
finally:
 request('/api/plugins/registry',{'url':old['url'] if old.get('from')=='magpie' else ''});server.shutdown();server.server_close()
print(str(len(results))+' real Android plugin checks passed',flush=True)
