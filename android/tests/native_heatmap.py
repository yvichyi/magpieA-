#!/usr/bin/env python3
"""Installed heatmap checks, using private loopback fixtures and real CoreApi auth."""
import argparse,datetime,json,os,pathlib,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlsplit,parse_qs

ROOT=pathlib.Path(__file__).resolve().parents[2]
ADB=os.environ.get('ADB','/workspace/toolchains/android-sdk/platform-tools/adb')
KEY='heatmap-fixture-key-1234567890'

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def reply(self,code,data):
        data=data if isinstance(data,bytes) else data.encode()
        self.send_response(code);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def do_POST(self):
        command=urlsplit(self.path).path.removeprefix('/_test/')
        if command=='fail':self.server.fail=True
        elif command=='ok':self.server.fail=False
        elif command=='hold':self.server.release.clear()
        elif command=='release':self.server.release.set()
        else:self.send_error(404);return
        self.reply(200,'ok')
    def do_GET(self):
        u=urlsplit(self.path);q=parse_qs(u.query)
        if u.path=='/_test/state':self.reply(200,str(self.server.reads));return
        if u.path=='/':
            if q.get('k')!=[KEY]:self.send_error(401);return
            self.send_response(302);self.send_header('Location','/');self.send_header('Set-Cookie','heat_fixture='+self.server.label+'; Path=/; HttpOnly');self.end_headers();return
        if self.headers.get('Cookie')!='heat_fixture='+self.server.label:self.send_error(401);return
        if u.path=='/api/usage':
            data=json.loads((ROOT/'android/tests/fixtures/usage-core-today.json').read_text());data['period']=q.get('period',['today'])[0]
            if data['period']!='today':data['bucket']='day'
            self.reply(200,json.dumps(data));return
        if u.path=='/api/usage/heatmap':
            self.server.reads+=1
            if self.server.fail:self.reply(503,'Unavailable');return
            assert q.get('tz'), 'Phone timezone missing'
            today=datetime.datetime.now(datetime.timezone.utc).date();monday=today-datetime.timedelta(days=today.weekday())
            body=json.dumps({'from':str(monday-datetime.timedelta(weeks=52)),'to':str(today),'days':[{'date':str(today-datetime.timedelta(days=2)),'calls':0,'tokens':120,'cost':0,'unpriced':1},{'date':str(today-datetime.timedelta(days=1)),'calls':5,'tokens':1000,'cost':0.125},{'date':str(today),'calls':self.server.calls,'tokens':2400,'cost':0,'unpriced':self.server.calls+1}]}).encode()
            # Valid JSON whitespace keeps a genuinely pending old read alive;
            # it is completed only after the new workspace has been displayed.
            self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Connection','close');self.end_headers()
            try:
                end=time.monotonic()+90
                while not self.server.release.is_set() and time.monotonic()<end:
                    self.wfile.write(b' ');self.wfile.flush();self.server.release.wait(1)
                self.wfile.write(body);self.wfile.flush()
            except (BrokenPipeError,ConnectionResetError):pass
            return
        self.send_error(404)

def adb(*args,timeout=240):
    p=subprocess.run([ADB,*map(str,args)],text=True,capture_output=True,timeout=timeout)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout

def main():
    p=argparse.ArgumentParser();p.add_argument('--tests',required=True);p.add_argument('--baseline',action='store_true');p.add_argument('--mode',default='normal');p.add_argument('--out',default=str(ROOT/'android/verification/1.12'));args=p.parse_args()
    out=pathlib.Path(args.out);out.mkdir(parents=True,exist_ok=True)
    servers=[]
    for label,calls in [('a',17),('b',29)]:
        s=ThreadingHTTPServer(('127.0.0.1',0),Handler);s.daemon_threads=True;s.label=label;s.calls=calls;s.reads=0;s.fail=False;s.release=threading.Event();s.release.set()
        threading.Thread(target=s.serve_forever,daemon=True).start();servers.append(s);port=s.server_address[1];adb('reverse','tcp:'+str(port),'tcp:'+str(port))
    try:
        assert 'Success' in adb('install','--no-streaming','--no-incremental','-r',args.tests,timeout=600)
        cmd=['shell','am','instrument','-w','-r','-e','mode','present' if args.baseline else args.mode]
        for n,s in zip(['address','addressB'],servers):cmd+=['-e',n,'http://127.0.0.1:'+str(s.server_address[1])]
        text=adb(*cmd,'ai.magpie.android.tests/ai.magpie.android.NativeHeatmapAcceptance',timeout=480)
        (out/('heatmap-'+('baseline-red' if args.baseline else args.mode)+'.log')).write_text(text)
        if args.baseline:assert 'Native Usage has no daily heatmap section' in text,text
        else:assert 'INSTRUMENTATION_CODE: -1' in text,text
        print(text,flush=True)
        if not args.baseline:
            dest=ROOT/'android/design/1.12'/args.mode;dest.mkdir(parents=True,exist_ok=True)
            adb('pull','/data/user/0/ai.magpie.android/files/heatmap-evidence/.',dest)
    finally:
        for s in servers:s.release.set();adb('reverse','--remove','tcp:'+str(s.server_address[1]));s.shutdown();s.server_close()

if __name__=='__main__':main()
