package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

/** Hardware display lists shared between the scene and the small dock sampling region. */
final class GlassBlur31 implements GlassFrame.Backdrop {
    private final RenderNode scene = new RenderNode("Magpie native scene");
    private final RenderNode lens = new RenderNode("Magpie dock backdrop");
    private final GlassPolicy.Mode mode;
    private final float density;
    private final int pad;
    private int lastWidth, lastHeight;
    private float lastRadius;

    GlassBlur31(GlassPolicy.Mode mode, float density) {
        this.mode = mode;
        this.density = density;
        pad = (int) Math.ceil(18 * density);
    }

    @Override public Canvas beginScene(int width, int height) {
        scene.setPosition(0, 0, width, height);
        return scene.beginRecording(width, height);
    }

    @Override public void endScene() { scene.endRecording(); }

    @Override public void drawScene(Canvas canvas) { canvas.drawRenderNode(scene); }

    @Override public boolean drawLens(Canvas canvas, int left, int top, int width, int height, float radius) {
        if (!scene.hasDisplayList() || width <= 0 || height <= 0) return false;
        int paddedWidth = width + 2 * pad;
        int paddedHeight = height + 2 * pad;
        if (lastWidth != width || lastHeight != height || lastRadius != radius) {
            RenderEffect effect;
            if (mode == GlassPolicy.Mode.REFRACT && Build.VERSION.SDK_INT >= 33) {
                effect = GlassShader33.create(width, height, radius, pad, density);
            } else {
                effect = RenderEffect.createBlurEffect(6 * density, 6 * density, Shader.TileMode.CLAMP);
            }
            lens.setPosition(-pad, -pad, width + pad, height + pad);
            lens.setRenderEffect(effect);
            lastWidth = width;
            lastHeight = height;
            lastRadius = radius;
        }
        RecordingCanvas region = lens.beginRecording(paddedWidth, paddedHeight);
        try {
            region.translate(pad - left, pad - top);
            region.drawRenderNode(scene);
        } finally {
            lens.endRecording();
        }
        canvas.drawRenderNode(lens);
        return true;
    }

    @Override public void release() {
        lens.discardDisplayList();
        scene.discardDisplayList();
    }
}
