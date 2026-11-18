package ai.magpie.android;

public final class GlassPolicyTest {
    public static void main(String[] args) {
        int cases = 0;
        for (int api = 28; api <= 36; api++) {
            for (int flags = 0; flags < 8; flags++) {
                boolean accelerated = (flags & 1) != 0;
                boolean enabled = (flags & 2) != 0;
                boolean solid = (flags & 4) != 0;
                GlassPolicy.Mode expected = GlassPolicy.Mode.SOLID;
                if (accelerated && enabled && !solid && api >= 31) {
                    expected = api >= 33 ? GlassPolicy.Mode.REFRACT : GlassPolicy.Mode.BLUR;
                }
                GlassPolicy.Mode got = GlassPolicy.choose(api, accelerated, enabled, solid);
                if (got != expected) throw new AssertionError("API " + api + ", flags " + flags + ": " + got);
                cases++;
            }
        }
        System.out.println("PASS " + cases + " glass capability and accessibility combinations");
    }
}
