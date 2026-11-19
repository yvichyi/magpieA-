package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** Each chrome surface samples the same hardware display list. */
final class GlassBlur31 implements GlassFrame.Backdrop {
    private final RenderNode scene = new RenderNode("Magpie native scene");
    private final RenderNode background = new RenderNode("Magpie material background");
    private final Lens dock = new Lens("Magpie dock backdrop");
    private final Lens toolbar = new Lens("Magpie toolbar backdrop");
    private final GlassPolicy.Mode mode;
    private final float density;
    private final int pad;
    private Drawable recordedBackground;
    private int sceneWidth, sceneHeight;

    private static final class Lens {
        final RenderNode node;
        int left, top, width, height;
        float radius;

        Lens(String name) { node = new RenderNode(name); }
    }

    GlassBlur31(GlassPolicy.Mode mode, float density) {
        this.mode = mode;
        this.density = density;
        // The refractive path needs 15dp displacement plus its local softening taps.
        pad = (int) Math.ceil((mode == GlassPolicy.Mode.REFRACT ? 18 : 25) * density);
    }

    @Override public Canvas beginScene(int width, int height, Drawable material) {
        if (width != sceneWidth || height != sceneHeight || material != recordedBackground) {
            scene.setPosition(0, 0, width, height);
            background.setPosition(0, 0, width, height);
            RecordingCanvas region = background.beginRecording(width, height);
            try {
                material.draw(region);
            } finally {
                background.endRecording();
            }
            sceneWidth = width;
            sceneHeight = height;
            recordedBackground = material;
        }
        return scene.beginRecording(width, height);
    }

    @Override public void endScene() { scene.endRecording(); }

    @Override public void drawScene(Canvas canvas) { canvas.drawRenderNode(scene); }

    @Override public boolean drawLens(Canvas canvas, boolean topSurface,
            int left, int top, int width, int height, float radius) {
        if (!scene.hasDisplayList() || width <= 0 || height <= 0) return false;
        Lens surface = topSurface ? toolbar : dock;
        RenderNode lens = surface.node;
        boolean resized = surface.width != width || surface.height != height || surface.radius != radius;
        if (resized) {
            RenderEffect effect;
            if (mode == GlassPolicy.Mode.REFRACT && Build.VERSION.SDK_INT >= 33) {
                effect = GlassShader33.create(width, height, radius, pad, density);
            } else {
                effect = RenderEffect.createBlurEffect(6 * density, 6 * density, Shader.TileMode.CLAMP);
            }
            lens.setPosition(-pad, -pad, width + pad, height + pad);
            lens.setRenderEffect(effect);
            surface.width = width;
            surface.height = height;
            surface.radius = radius;
        }
        // Child and background updates reach this display list through their shared nodes.
        if (resized || surface.left != left || surface.top != top || !lens.hasDisplayList()) {
            RecordingCanvas region = lens.beginRecording(width + 2 * pad, height + 2 * pad);
            try {
                region.translate(pad - left, pad - top);
                region.drawRenderNode(background);
                region.drawRenderNode(scene);
            } finally {
                lens.endRecording();
            }
            surface.left = left;
            surface.top = top;
        }
        canvas.drawRenderNode(lens);
        return true;
    }

    @Override public void release() {
        dock.node.discardDisplayList();
        toolbar.node.discardDisplayList();
        scene.discardDisplayList();
        background.discardDisplayList();
    }
}
