"""Native UI acceptance on an explicitly disposable, rooted emulator only.

Requires installed 1.3 APK. Creates real local-upstream calls using android_smoke.py;
never injects demonstration counts into the app. No physical-phone root requirement.
"""
from pathlib import Path
import subprocess, os, time, json, xml.etree.ElementTree as ET, re

ROOT=Path(__file__).resolve().parents[2]
ADB=os.environ.get('ADB','/workspace/toolchains/android-sdk/platform-tools/adb')
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
EVIDENCE=ROOT/'android/build/evidence'
EVIDENCE.mkdir(parents=True,exist_ok=True)

def adb(*args,timeout=45):
    return subprocess.check_output([ADB,'-s',SERIAL,*args],timeout=timeout)

def dump():
    adb('shell','uiautomator','dump','/data/local/tmp/magpie-usage-ui.xml',timeout=50)
    return ET.fromstring(adb('exec-out','cat','/data/local/tmp/magpie-usage-ui.xml'))

def words(root):
    return ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in root.iter('node'))

def tap_description(root,label):
    for node in root.iter('node'):
        if node.get('content-desc','')==label or node.get('text','')==label:
            nums=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
            if len(nums)==4:
                adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2));return
    raise AssertionError('Missing native control: '+label)

def capture(name):
    # The software-only image occasionally captures a black transition frame.
    # Retain it as evidence, and wait for a genuinely rendered frame.
    import io
    from PIL import Image, ImageStat
    for i in range(4):
        raw=adb('exec-out','screencap','-p')
        mean=ImageStat.Stat(Image.open(io.BytesIO(raw)).convert('L')).mean[0]
        if mean>20 and len(raw)>8000:
            (EVIDENCE/name).write_bytes(raw);return
        (EVIDENCE/(name+'.transition-'+str(i)+'.png')).write_bytes(raw)
        time.sleep(1)
    raise AssertionError('Only blank transition frames were captured: '+name)

def swipe_content(tree,up=True):
    scroll=next(n for n in tree.iter('node') if n.get('scrollable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
    start=y1+int((y2-y1)*.78);end=y1+int((y2-y1)*.22)
    if not up:start,end=end,start
    adb('shell','input','swipe',str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),'1000')

def dismiss_system_anr():
    # Some software-only images show System UI ANRs. Never confuse one with app success.
    tree=dump()
    if 'System UI' in words(tree) and ('Wait' in words(tree) or '等待' in words(tree)):
        tap_description(tree,'Wait' if 'Wait' in words(tree) else '等待')
        return dump()
    return tree

results=[]
def passed(message):
    results.append(message);print('PASS',message,flush=True)

assert adb('shell','id','-u').decode().strip()=='0','Use adb root on the disposable emulator first'
package=adb('shell','dumpsys','package','ai.magpie.android').decode()
assert 'versionName=1.3.0' in package,'Install the current APK first'
adb('shell','settings','put','system','screen_off_timeout','1800000')
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','input','keyevent','224')
adb('shell','am','start','-W','-n','ai.magpie.android/.MainActivity',timeout=60)
tree=dismiss_system_anr()
text=words(tree)
if '启动本机网关' in text:tap_description(tree,'启动本机网关')
for _ in range(60):
    pid=subprocess.run([ADB,'-s',SERIAL,'shell','pidof','libmagpie.so'],capture_output=True,timeout=30)
    if pid.returncode==0 and pid.stdout.strip():break
    time.sleep(1)
else:raise AssertionError('Owned gateway process did not start')
# Allow the actual owned management listener to bind, without recording its key.
for _ in range(60):
    tree=dismiss_system_anr()
    if '运行中' in words(tree):break
    time.sleep(1)
else:raise AssertionError('Native dashboard never reported gateway ready')
subprocess.run(['python3',str(ROOT/'android/tests/android_smoke.py')],check=True,timeout=180)
capture('statistics-home-1.3.png')
adb('shell','am','start','-W','-n','ai.magpie.android/.UsageActivity',timeout=60)
for _ in range(4):
    tree=dismiss_system_anr()
    if '更新于' in words(tree):break
else:raise AssertionError('Native statistics did not finish reading the actual core')
text=words(tree)
assert '调用次数' in text and '完成率' in text and '待定价' in text,text
capture('statistics-summary-1.3.png')
passed('Native statistics renders authenticated actual Android-core usage and unknown prices')
chart=next(n for n in tree.iter('node') if n.get('content-desc','').startswith('调用趋势，共'))
tap_description(tree,chart.get('content-desc'))
tree=dismiss_system_anr()
assert any('次调用' in n.get('content-desc','') for n in tree.iter('node')),'Chart selection did not expose a bucket'
passed('Native chart selection reveals the actual bucket values')
for _ in range(3):
    swipe_content(tree)
    tree=dismiss_system_anr()
    if '模型排行' in words(tree):break
capture('statistics-models-1.3.png')
assert '模型排行' in words(tree) and 'android-echo' in words(tree),words(tree)
passed('Native token breakdown and actual model ranking are reachable by scrolling')
for _ in range(3):
    swipe_content(tree,False);tree=dismiss_system_anr()
    if '近 7 天' in words(tree):break
tap_description(tree,'近 7 天')
for _ in range(4):
    tree=dismiss_system_anr()
    if '更新于' in words(tree) and '近 7 天，已选择' in words(tree):break
else:raise AssertionError('7-day native period did not settle')
capture('statistics-week-1.3.png')
passed('Native period selector switches to the real seven-day summary')
try:
    adb('shell','settings','put','system','font_scale','1.5')
    adb('shell','am','start','-W','-n','ai.magpie.android/.UsageActivity',timeout=60)
    tree=dismiss_system_anr()
    capture('statistics-large-font-1.3.png')
    assert '用量统计' in words(tree) and '近 30 天' in words(tree),words(tree)
    passed('Native statistics remains reachable at font scale 1.5; screenshot requires visual review')
finally:
    adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','stopservice','-n','ai.magpie.android/.GatewayService')
tree=dismiss_system_anr();tap_description(tree,'刷新统计')
for _ in range(3):
    tree=dismiss_system_anr()
    if '统计暂不可用' in words(tree):break
else:raise AssertionError('Stopped gateway was not reported as unavailable')
capture('statistics-unavailable-1.3.png')
passed('Stopped gateway is unavailable, not a successful empty summary')
(EVIDENCE/'native-usage-results.json').write_text(json.dumps({'passed':results},ensure_ascii=False,indent=2))
print(str(len(results))+' native usage checks passed',flush=True)
