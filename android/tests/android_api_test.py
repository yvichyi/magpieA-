#!/usr/bin/env python3
"""Check the three native query encoders against the declared Android API floor.

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
encoder=api.find("class[@name='java/net/URLEncoder']")
methods={m.get('name'):int(m.get('since',encoder.get('since','1'))) for m in encoder.findall('method')}
javap=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/javap') if os.environ.get('JAVA_HOME') else 'javap'
targets=['ai.magpie.android.'+n for n in ['CoreApi','UsageSnapshot','UsageHeatmap']]
text=subprocess.check_output([javap,'-verbose','-classpath',str(classes),*targets],text=True)
references=set(re.findall(r'java/net/URLEncoder\.(\w+):(\S+)',text))
assert references,'No query encoder linkage was examined'
for name,descriptor in sorted(references):
    method=name+descriptor
    assert method in methods,'Unknown SDK query method: '+method
    assert methods[method]<=floor,f'Query encoding requires API{methods[method]} but app supports API{floor}: URLEncoder.{method}'
print(f'PASS native auth/summary/heatmap URL encoder linkage supports minimum API{floor}')
