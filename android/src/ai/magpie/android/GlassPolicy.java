package ai.magpie.android;

final class GlassPolicy {
    enum Mode { SOLID, BLUR, REFRACT }

    static Mode choose(int api, boolean accelerated, boolean enabled, boolean solid, boolean still) {
        if (!accelerated || !enabled || solid || api < 31) return Mode.SOLID;
        return api >= 33 && !still ? Mode.REFRACT : Mode.BLUR;
    }

    private GlassPolicy() {}
}
