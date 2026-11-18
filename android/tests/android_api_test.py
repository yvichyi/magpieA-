#!/usr/bin/env python3
"""Check native Java-library calls against the declared Android API floor.

Uses compiled method references and the SDK API database. This is a linkage
check, not a claim that the app was executed on every Android release.
"""
import os,pathlib,re,subprocess,sys,xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[2]
classes=pathlib.Path(sys.argv[1])
sdk=pathlib.Path(sys.argv[2] if len(sys.argv)>2 else os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME','/workspace/toolchains/android-sdk')))
manifest=ET.parse(root/'android/AndroidManifest.xml').getroot()
floor=int(manifest.find('uses-sdk').get('{http://schemas.android.com/apk/res/android}minSdkVersion'))
api=ET.parse(sdk/'platforms/android-35/data/api-versions.xml').getroot()
javap=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/javap') if os.environ.get('JAVA_HOME') else 'javap'
targets=[p.relative_to(classes).as_posix()[:-6].replace('/','.')
         for p in classes.rglob('*.class') if p.name not in {'R.class','BuildInfo.class'} and not p.name.startswith('R$')]
text=subprocess.check_output([javap,'-verbose','-classpath',str(classes),*targets],text=True)
methods={c.get('name'):{m.get('name'):int(m.get('since',c.get('since','1'))) for m in c.findall('method')}
         for c in api.findall('class') if c.get('name').startswith('java/')}
references=set(re.findall(r'// (java/[^.\s]+)\.([\w<>\"]+):(\S+)',text))
assert any(owner=='java/net/URLEncoder' for owner,_,_ in references),'No query encoder linkage was examined'
test_classes={p.relative_to(classes).as_posix()[:-6].replace('/','.') for p in classes.rglob('*Test.class')}
for block in re.split(r'(?=^Classfile )',text,flags=re.M):
    if not block.startswith('Classfile '):continue
    owner_name=block.splitlines()[0].split('/ai/magpie/android/')[-1].removesuffix('.class')
    if owner_name.split('$')[0].endswith('Test') or 'ai.magpie.android.'+owner_name in test_classes:continue
    for owner,name,descriptor in sorted(set(re.findall(r'// (java/[^.\s]+)\.([\w<>\"]+):(\S+)',block))):
        # D8 rewrites Java records to its own record tag at min API28.
        if owner in {'java/lang/Record','java/lang/runtime/ObjectMethods'}:continue
        method=name.replace('"','')+descriptor
        level=methods.get(owner,{}).get(method,0)
        if owner=='java/net/URLEncoder':assert level,'Unknown SDK query method: '+method
        assert level<=floor,f'{owner_name} calls API{level} {owner}.{method}, min API{floor}'
print(f'PASS native Java-library linkage supports minimum API{floor}')
