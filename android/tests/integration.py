"""Real Magpie gateway against a deterministic local upstream; no vendor credentials."""
import json, threading, urllib.request, urllib.error, http.cookiejar, pathlib, time
import os, sys, socket, tempfile, subprocess, atexit
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 0
records = []
class Upstream(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        self.send_response(200); self.send_header('Content-Type','application/json');self.end_headers()
        self.wfile.write(json.dumps({'object':'list','data':[{'id':'echo','object':'model'},{'id':'limited','object':'model'}]}).encode())
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        records.append(body)
        if body.get('model')=='limited':
            self.send_response(429);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{"error":{"message":"test quota exhausted","type":"rate_limit_error"}}');return
        self.send_response(200)
        if body.get('stream'):
            self.send_header('Content-Type','text/event-stream');self.end_headers()
            for delta,finish in [({'role':'assistant','content':'Android '},None),({'content':'gateway OK'},None),({},'stop')]:
                event={'id':'chatcmpl-local-test','object':'chat.completion.chunk','model':'echo','choices':[{'index':0,'delta':delta,'finish_reason':finish}]}
                self.wfile.write(('data: '+json.dumps(event)+'\n\n').encode());self.wfile.flush()
            # Standard final Chat chunk: translated Messages also requests a
            # stream upstream, so without this that fixture has no token report.
            usage={'id':'chatcmpl-local-test','object':'chat.completion.chunk','model':'echo','choices':[],
                   'usage':{'prompt_tokens':12,'completion_tokens':4,'total_tokens':16}}
            self.wfile.write(('data: '+json.dumps(usage)+'\n\n').encode())
            self.wfile.write(b'data: [DONE]\n\n');self.wfile.flush()
        else:
            self.send_header('Content-Type','application/json');self.end_headers()
            self.wfile.write(json.dumps({'id':'chatcmpl-local-test','object':'chat.completion','model':'echo','choices':[{'index':0,'message':{'role':'assistant','content':'Android gateway OK'},'finish_reason':'stop'}],'usage':{'prompt_tokens':12,'completion_tokens':4,'total_tokens':16}}).encode())

server=ThreadingHTTPServer(('127.0.0.1',PORT),Upstream)
PORT=server.server_address[1]
threading.Thread(target=server.serve_forever,daemon=True).start()
# Every run owns isolated files and ports; external vendor calls are disabled.
def unused_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1',0));return s.getsockname()[1]
web_port,gateway_port=unused_port(),unused_port()
root=pathlib.Path(__file__).resolve().parents[2]
evidence=root/'android/build/evidence';evidence.mkdir(parents=True,exist_ok=True)
temporary=tempfile.TemporaryDirectory(prefix='magpie-android-qa-')
home=pathlib.Path(temporary.name);config=home/'.config/magpie';config.mkdir(parents=True)
(home/'Downloads').mkdir()
(config/'settings.json').write_text(json.dumps({'lang':'zh','port':gateway_port,'noAutoUpdate':True}))
core=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else root/'android/build/magpie-host'
environment={'PATH':os.environ.get('PATH','/usr/bin:/bin'),'HOME':str(home),'XDG_CONFIG_HOME':str(home/'.config'),'XDG_CACHE_HOME':str(home/'cache'),'XDG_DATA_HOME':str(home/'data'),'XDG_STATE_HOME':str(home/'state'),'MAGPIE_NO_ACCOUNTS':'1','MAGPIE_WEB_KEY':'android-test-key-1234567890123456','DO_NOT_TRACK':'1','MAGPIE_NO_STATS':'1','HTTP_PROXY':'http://127.0.0.1:1','HTTPS_PROXY':'http://127.0.0.1:1','NO_PROXY':'127.0.0.1,localhost'}
log=open(evidence/'integration-core.log','w')
process=subprocess.Popen([str(core),'serve',f'--web=127.0.0.1:{web_port}'],env=environment,stdout=log,stderr=subprocess.STDOUT)
def cleanup():
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
    server.shutdown();log.close();temporary.cleanup()
atexit.register(cleanup)
WEB=f'http://127.0.0.1:{web_port}';GATEWAY=f'http://127.0.0.1:{gateway_port}'
probe=urllib.request.build_opener(urllib.request.ProxyHandler({}))
for attempt in range(100):
    try:probe.open(WEB+'/manifest.json',timeout=1).read();break
    except Exception:
        if process.poll() is not None:raise RuntimeError('core exited; see '+str(evidence/'integration-core.log'))
        time.sleep(.1)
else:raise RuntimeError('core did not start')

cookies=http.cookiejar.CookieJar()
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies),urllib.request.ProxyHandler({}))
client.open(WEB+'/?k=android-test-key-1234567890123456').read()

def call(path,body=None,gateway=False):
    url=(GATEWAY if gateway else WEB)+path
    headers={'Content-Type':'application/json'}
    if gateway:headers['Authorization']='Bearer magpie'
    req=urllib.request.Request(url,None if body is None else json.dumps(body).encode(),headers=headers)
    with client.open(req,timeout=45) as response:return response.read()
def obj(path,body=None,gateway=False):return json.loads(call(path,body,gateway))

results=[]
def check(name,fn):
    fn();results.append(name);print('PASS',name,flush=True)

def auth():
    plain=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try: plain.open(WEB+'/api/providers');raise AssertionError('unprotected API')
    except urllib.error.HTTPError as e:assert e.code==401
check('Management API refuses unauthenticated requests',auth)

def provider():
    r=obj('/api/provider/save',{'id':'android-qa','name':'Android 验证服务商','chat':f'http://127.0.0.1:{PORT}/v1','key':'local-fixture-key','models':['echo','limited']})
    assert not r.get('error'),r
    saved=obj('/api/providers')['providers'];assert any(p['id']=='android-qa' for p in saved)
check('Provider CRUD persists through the real management API',provider)

def models():
    r=obj('/v1/models',gateway=True);assert any(m['id']=='android-qa/echo' for m in r['data'])
check('Gateway lists configured models',models)

def chat():
    r=obj('/v1/chat/completions',{'model':'android-qa/echo','messages':[{'role':'user','content':'test Android'}]},True)
    assert r['choices'][0]['message']['content']=='Android gateway OK',r
    assert records[-1]['messages'][-1]['content']=='test Android'
check('OpenAI completion forwards and returns real upstream data',chat)

def stream():
    r=call('/v1/chat/completions',{'model':'android-qa/echo','messages':[{'role':'user','content':'test stream'}],'stream':True},True).decode()
    assert 'Android ' in r and 'gateway OK' in r and '[DONE]' in r,r
check('SSE streaming forwards incremental chunks',stream)

def anthropic():
    r=obj('/v1/messages',{'model':'android-qa/echo','messages':[{'role':'user','content':'test translation'}],'max_tokens':32},True)
    assert r['content'][0]['text']=='Android gateway OK',r
check('Anthropic Messages converts to the configured OpenAI upstream',anthropic)

def route():
    r=obj('/api/groups/save',{'id':'android-qa-fallback','name':'Android 备用路由','routing':'order','members':['android-qa/limited','android-qa/echo']})
    assert not r.get('error'),r
    r=obj('/v1/chat/completions',{'model':'group/android-qa-fallback','messages':[{'role':'user','content':'test fallback'}]},True)
    assert r['choices'][0]['message']['content']=='Android gateway OK',r
    assert any(x.get('model')=='limited' for x in records)
check('Ordered route falls back after upstream quota refusal',route)

def usage_summary():
    for period in ['today','7d','30d','all']:
        summary=obj('/api/usage?period='+period+'&tz=Asia%2FShanghai')
        (evidence/('usage-core-'+period+'.json')).write_text(json.dumps(summary,ensure_ascii=False))
        assert summary['period']==period,summary
        assert summary['calls']==4 and summary['errors']==0,summary
        assert summary.get('retried')==1,summary
        assert summary['input']==48 and summary['output']==16,summary
        assert summary['unpriced']==4 and summary['cost']==0,summary
        assert summary['models'] and summary['series'],summary
        assert sum(point['calls'] for point in summary['series'])==4,summary
check('Usage periods/timezone, retry counts, actual tokens and unknown prices match the real ledger',usage_summary)

def usage_heatmap():
    heat=obj('/api/usage/heatmap?tz=Asia%2FShanghai')
    assert heat['from'] <= heat['to'] and sum(d['calls'] for d in heat['days']) == 4, heat
    assert sum(d['unpriced'] for d in heat['days']) == 4, heat
    (evidence/'usage-heatmap-core.json').write_text(json.dumps(heat,ensure_ascii=False))
check('Native heatmap source matches the actual daily ledger and partial prices',usage_heatmap)

def backup():
    r=obj('/api/backup/export',{'pass':'android-test-passphrase','keys':True,'library':True})
    assert r.get('path'),r
    path=pathlib.Path(r['path'].replace('~',str(home),1))
    raw=path.read_bytes();assert len(raw)>64
    assert b'local-fixture-key' not in raw,'backup must be sealed'
    import base64
    wrong={'data':base64.b64encode(raw).decode(),'pass':'incorrect','agents':False,'library':False}
    try:
        bad=obj('/api/backup/import',wrong)
        assert bad.get('error'),bad
    except urllib.error.HTTPError as e:assert e.code in (400,500)
    restored=obj('/api/backup/import',{**wrong,'pass':'android-test-passphrase'})
    assert 'error' not in restored,restored
check('Encrypted backup export, wrong-pass rejection and restore',backup)

check('Real upstream provider edit and add dispatch',lambda:subprocess.run(['node','android/tests/editor-real.cjs',WEB],cwd=root,check=True))
check('Real management controls remain reachable on constrained phones',lambda:subprocess.run(['node','android/tests/layout-real.cjs',WEB],cwd=root,check=True))

# Native gateway formats are exercised after ledger/backup assertions so their
# deliberate requests do not alter the existing accounting fixtures.
if os.environ.get('MAGPIE_PROBE_TEST_CP'):
    check('Native gateway probe uses all three real protocol translators',lambda:subprocess.run([
        os.environ.get('MAGPIE_TEST_JAVA','java'),'-cp',os.environ['MAGPIE_PROBE_TEST_CP'],
        'ai.magpie.android.GatewayProbeTest',GATEWAY,'android-qa/echo'],check=True))

(evidence/'integration-results.json').write_text(json.dumps({'passed':results,'upstreamRequests':len(records)},ensure_ascii=False,indent=2))
print(f'{len(results)} integration checks passed')
