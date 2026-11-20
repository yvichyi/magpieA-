#!/usr/bin/env python3
"""Verify the signed preview in a disposable rooted Android SDK emulator."""
import argparse
import gzip
from datetime import datetime, timedelta, timezone
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import threading
import time
import urllib.parse
import urllib.request
from uuid import uuid4
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser()
parser.add_argument('--mode', choices=['flow', 'workflow', 'plugins', 'stats', 'small', 'glass', 'themes',
                                      'typography', 'typography-small', 'render-benchmark', 'checkin'], default='flow')
parser.add_argument('--evidence', type=Path, required=True)
args = parser.parse_args()
ADB = os.environ.get('ADB', '/workspace/toolchains/android-sdk/platform-tools/adb')
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PACKAGE = 'ai.magpie.android'
PLUGIN_PREFIX = 'magpie-android-preview-fixture'
# Bun retains downloaded modules across test runs, including after uninstall.
PLUGIN = f'{PLUGIN_PREFIX}-{uuid4().hex[:8]}'
args.evidence.mkdir(parents=True, exist_ok=True)


def adb(*parts, timeout=55):
    result = subprocess.run([ADB, '-s', SERIAL, *parts], capture_output=True, timeout=timeout)
    if result.returncode:
        raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)
    return result.stdout


if not SERIAL.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
    raise RuntimeError('Preview fixtures require a disposable SDK emulator')


class Fixture(BaseHTTPRequestHandler):
    version = '1.0.1'
    installs = []
    invocations = []
    checkin_on = True
    checkin_result = []
    checkin_posts = []

    def log_message(self, *ignored):
        pass

    def reply(self, value, media='application/json'):
        if not isinstance(value, bytes):
            value = json.dumps(value).encode()
        self.send_response(200)
        self.send_header('Content-Type', media)
        self.send_header('Content-Length', str(len(value)))
        self.end_headers()
        self.wfile.write(value)

    def archive(self, version):
        output = io.BytesIO()
        js = b'export const PreviewFixture = async () => ({});'
        files = {'package.json': json.dumps({'name': PLUGIN, 'version': version, 'type': 'module', 'main': 'index.js'}).encode(),
                 'index.js': js}
        with tarfile.open(fileobj=output, mode='w') as archive:
            for name, data in files.items():
                entry = tarfile.TarInfo('package/' + name)
                entry.size = len(data)
                archive.addfile(entry, io.BytesIO(data))
        return gzip.compress(output.getvalue(), mtime=0)

    def do_GET(self):
        if args.mode == 'checkin':
            if self.path == '/?k=android-checkin-test-key':
                self.send_response(302)
                self.send_header('Set-Cookie', 'fixture_checkin=ok; Path=/; HttpOnly')
                self.send_header('Location', '/')
                self.send_header('Content-Length', '0')
                self.end_headers()
                return
            if self.headers.get('Cookie') != 'fixture_checkin=ok':
                return self.send_error(401)
            if self.path == '/api/settings':
                return self.reply(self.checkin_settings())
            if self.path == '/api/usage/checkins/off':
                return self.reply([])
            return self.send_error(404)
        if self.path == '/_advance':
            Fixture.version = '1.0.2'
            return self.reply({'version': Fixture.version})
        if self.path.startswith('/v1/models'):
            models = ['preview-echo', 'preview-second']
            if args.mode == 'workflow':
                models.append('preview-vision-reasoning-multilingual-long-context-2026-11')
            return self.reply({'object': 'list', 'data': [{'id': name} for name in models]})
        if '.tgz' in self.path:
            version = self.path.removesuffix('.tgz').split('-')[-1]
            Fixture.installs.append(version)
            return self.reply(self.archive(version), 'application/octet-stream')
        ver = Fixture.version
        archive = self.archive(ver)
        version = {'name': PLUGIN, 'version': ver, 'type': 'module', 'main': 'index.js',
                   'dist': {'tarball': f'{origin}/{PLUGIN}/-/{PLUGIN}-{ver}.tgz',
                            'shasum': hashlib.sha1(archive).hexdigest()}}
        if self.path.split('?', 1)[0].endswith('/latest'):
            return self.reply(version)
        return self.reply({'name': PLUGIN, 'description': 'Android SDK preview fixture',
                           'dist-tags': {'latest': ver}, 'versions': {ver: version}})

    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))) or b'{}')
        if args.mode == 'checkin':
            if self.headers.get('Cookie') != 'fixture_checkin=ok' or self.headers.get('Origin') != origin:
                return self.send_error(403)
            if self.path == '/api/usage/checkin-account' and data == {'by': 'plugin:fixture', 'key': 'fixture|account1'}:
                day = datetime.now(timezone(timedelta(hours=8))).date().isoformat()
                result = {'day': day, 'outcome': 'claimed', 'asked': True, 'credit': 10}
                Fixture.checkin_result = [dict(result, user='验收账号')]
                Fixture.checkin_posts.append((self.path, data))
                return self.reply(result)
            if self.path == '/api/settings/checkin-account' and data == {'key': 'fixture|account1', 'on': False}:
                Fixture.checkin_on = False
                Fixture.checkin_posts.append((self.path, data))
                return self.reply(self.checkin_settings())
            return self.send_error(400)
        Fixture.invocations.append(self.path)
        model = data.get('model', 'preview-echo')
        usage = {'prompt_tokens': 13, 'completion_tokens': 3, 'total_tokens': 16}
        if data.get('stream'):
            chunks = []
            for delta, finish in [({'role': 'assistant', 'content': 'OK'}, None), ({}, 'stop')]:
                chunks.append({'id': 'preview', 'object': 'chat.completion.chunk', 'model': model,
                               'choices': [{'index': 0, 'delta': delta, 'finish_reason': finish}]})
            chunks.append({'id': 'preview', 'object': 'chat.completion.chunk', 'model': model, 'choices': [], 'usage': usage})
            return self.reply((''.join('data: ' + json.dumps(chunk) + '\n\n' for chunk in chunks) +
                               'data: [DONE]\n\n').encode(), 'text/event-stream')
        return self.reply({'id': 'preview', 'object': 'chat.completion', 'model': model, 'usage': usage,
                           'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': 'OK'}, 'finish_reason': 'stop'}]})

    @staticmethod
    def checkin_settings():
        return {
            'checkinAccounts': [{'by': 'plugin:fixture', 'key': 'fixture|account1', 'user': '验收账号',
                                 'on': Fixture.checkin_on}],
            'checkinPlugins': [{'id': 'fixture', 'name': '验收服务商', 'on': True,
                                'checkins': Fixture.checkin_result}],
        }


def management():
    pid = adb('shell', 'pidof', 'libmagpie.so').decode().split()[0]
    environ = dict(value.split(b'=', 1) for value in adb('exec-out', 'cat', f'/proc/{pid}/environ').split(b'\0') if b'=' in value)
    if not environ.get(b'MAGPIE_BUN', b'').endswith(b'/libmagpie-bun.so'):
        raise RuntimeError('Native package runtime missing')
    key = environ[b'MAGPIE_WEB_KEY'].decode()
    links = adb('shell', f'for f in /proc/{int(pid)}/fd/*; do readlink "$f" || :; done').decode()
    inodes = set(re.findall(r'socket:\[(\d+)\]', links))
    ports = []
    for name in ['tcp', 'tcp6']:
        for line in adb('exec-out', 'cat', f'/proc/{pid}/net/{name}').decode().splitlines()[1:]:
            fields = line.split()
            if len(fields) > 9 and fields[3] == '0A' and fields[9] in inodes:
                ports.append(int(fields[1].split(':')[1], 16))
    port = next(port for port in ports if port != 3425)
    forwarded = int(adb('forward', 'tcp:0', f'tcp:{port}').strip())
    base = f'http://127.0.0.1:{forwarded}'
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                          urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    client.open(base + '/?k=' + urllib.parse.quote(key, safe=''), timeout=25).close()

    def request(path, body=None):
        data = None if body is None else json.dumps(body).encode()
        headers = {'Content-Type': 'application/json', 'Origin': base}
        with client.open(urllib.request.Request(base + path, data, headers), timeout=90) as reply:
            return json.load(reply)

    return request, forwarded


server = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
port = server.server_address[1]
origin = f'http://127.0.0.1:{port}'
threading.Thread(target=server.serve_forever, daemon=True).start()
old_font = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
old_size = adb('shell', 'wm', 'size').decode()
old_override = re.search(r'Override size: (\d+x\d+)', old_size)
try:
    adb('reverse', f'tcp:{port}', f'tcp:{port}')
    if args.mode in ('flow', 'plugins'):
        adb('shell', 'am', 'start-foreground-service', '-n', PACKAGE + '/.GatewayService')
        deadline = time.monotonic() + 160
        while True:
            try:
                api, forward = management()
                try:
                    for installed in api('/api/plugins').get('plugins', []):
                        spec = installed.get('spec', '')
                        name = spec.split('@', 1)[0]
                        if name == PLUGIN_PREFIX or re.fullmatch(re.escape(PLUGIN_PREFIX) + r'-[0-9a-f]{8}', name):
                            api('/api/plugins/remove', {'spec': spec})
                    api('/api/plugins/registry', {'url': origin})
                finally:
                    adb('forward', '--remove', f'tcp:{forward}')
                break
            except (OSError, StopIteration, IndexError, KeyError, subprocess.SubprocessError):
                if time.monotonic() >= deadline:
                    raise RuntimeError('Could not configure the disposable plugin registry') from None
                time.sleep(2)
    if args.mode in ('small', 'typography-small'):
        adb('shell', 'wm', 'size', '320x568')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    command = [ADB, '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'mode', args.mode,
               '-e', 'fixture', origin, '-e', 'plugin', PLUGIN, PACKAGE + '.tests/ai.magpie.android.NativePreviewAcceptance']
    log_path = args.evidence / (args.mode + '.log')
    with log_path.open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=960)
    data = log_path.read_text()
    print(data, flush=True)
    if result.returncode or 'INSTRUMENTATION_CODE: -1' not in data:
        raise RuntimeError('Installed preview acceptance failed')
    if args.mode in ('flow', 'plugins'):
        assert Fixture.installs == ['1.0.1', '1.0.2'], Fixture.installs
        print('PASS npm transport downloaded each plugin version once')
    if args.mode == 'flow':
        assert len(Fixture.invocations) == 3, Fixture.invocations
        print('PASS the gateway made exactly three test calls')
    elif args.mode == 'workflow':
        assert len(Fixture.invocations) == 1, Fixture.invocations
        print('PASS loopback provider received the model request from the signed APK')
    elif args.mode == 'checkin':
        assert [path for path, _ in Fixture.checkin_posts] == [
            '/api/usage/checkin-account', '/api/settings/checkin-account'
        ], Fixture.checkin_posts
        print('PASS authenticated manual check-in and automatic setting wrote their own exact routes once')
finally:
    try:
        path = f'/data/user/0/{PACKAGE}/files/preview-evidence/{args.mode}'
        raw = adb('exec-out', 'tar', '-c', '-C', path, '.', timeout=55)
        with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
            for item in archive.getmembers():
                if item.isfile() and item.name.endswith('.png') and item.size < 8 * 1024 * 1024:
                    (args.evidence / Path(item.name).name).write_bytes(archive.extractfile(item).read())
    except (subprocess.SubprocessError, OSError, tarfile.TarError):
        pass
    if args.mode in ('small', 'typography-small'):
        adb('shell', 'wm', 'size', old_override.group(1) if old_override else 'reset')
        if old_font == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', old_font)
    adb('reverse', '--remove', f'tcp:{port}')
    server.shutdown()
