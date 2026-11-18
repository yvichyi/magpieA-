package ai.magpie.android;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

/** AGSL samples the native scene at displaced coordinates near the rounded rim. */
final class GlassShader33 {
    private static final String SHADER = """
            uniform shader backdrop;
            uniform float2 center;
            uniform float2 halfSize;
            uniform float radius;
            uniform float density;

            half4 main(float2 p) {
                float2 v = p - center;
                float2 q = abs(v) - (halfSize - radius);
                float2 corner = max(q, float2(0.0));
                float distanceToRim = length(corner) + min(max(q.x, q.y), 0.0) - radius;
                float edge = 1.0 - smoothstep(0.0, 21.0 * density, -distanceToRim);
                float2 normal = sign(v) * corner / max(length(corner), 0.001);
                float bend = 15.0 * density * edge * edge;
                half4 sampleColor = backdrop.eval(p - normal * bend);
                float light = max(0.0, dot(normal, float2(-0.45, -0.89)));
                half glint = half(0.58 * light * edge * edge);
                return half4(mix(sampleColor.rgb, half3(sampleColor.a), glint), sampleColor.a);
            }
            """;

    static RenderEffect create(int width, int height, float radius, int pad, float density) {
        RuntimeShader glass = new RuntimeShader(SHADER);
        glass.setFloatUniform("halfSize", width * .5f, height * .5f);
        glass.setFloatUniform("center", pad + width * .5f, pad + height * .5f);
        glass.setFloatUniform("radius", radius);
        glass.setFloatUniform("density", density);
        RenderEffect refract = RenderEffect.createRuntimeShaderEffect(glass, "backdrop");
        RenderEffect soften = RenderEffect.createBlurEffect(2.6f * density, 2.6f * density, Shader.TileMode.CLAMP);
        return RenderEffect.createChainEffect(refract, soften);
    }

    private GlassShader33() {}
}
