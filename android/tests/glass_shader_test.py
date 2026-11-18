"""Host Skia pixel test for the exact AGSL embedded in the API 33 renderer.

Optional test dependency: skia-python. Device HWUI composition is tested separately.
"""
from pathlib import Path
import re

import skia


root = Path(__file__).resolve().parents[2]
source = (root / 'android/src/ai/magpie/android/GlassShader33.java').read_text()
code = re.search(r'SHADER = """(.*?)""";', source, re.DOTALL).group(1)
effect = skia.RuntimeEffect.MakeForShader(code)
builder = skia.RuntimeShaderBuilder(effect)

# 160 x 64 dock at (18, 18), including the production sample padding.
builder.setUniform('center', [98., 50.])
builder.setUniform('halfSize', [80., 32.])
builder.setUniform('radius', 26.)
builder.setUniform('density', 1.)


def render(base):
    scene = skia.Surface(196, 100)
    scene.getCanvas().clear(base)
    scene.getCanvas().drawRect(skia.Rect.MakeXYWH(0, 77, 196, 12),
                               skia.Paint(Color=skia.ColorRED))
    builder.setChild('backdrop', scene.makeImageSnapshot().makeShader())
    result = skia.Surface(196, 100)
    result.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
    return result.toarray(colorType=skia.ColorType.kRGBA_8888_ColorType)


for base, expected in [(skia.ColorBLUE, [0, 0, 255, 255]), (skia.ColorGREEN, [0, 255, 0, 255])]:
    pixels = render(base)
    assert list(pixels[50, 98]) == expected, 'Interior must sample the unshifted source'
    assert list(pixels[80, 98]) == expected, 'The rim must bend the red stripe away, sampling the source above it'
    assert list(pixels[19, 98]) != expected, 'Upper rim must catch light'

print('PASS host Skia compiles shipped AGSL, refracts a stripe, preserves the center and reacts to scene color')
