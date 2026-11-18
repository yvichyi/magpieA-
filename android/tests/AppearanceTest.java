package ai.magpie.android;

public final class AppearanceTest {
    public static void main(String[] args) {
        require(Appearance.fromId(null) == Appearance.GLASS, "Existing installs need the glass default");
        require(Appearance.fromId("unknown") == Appearance.GLASS, "Unknown theme needs a safe default");
        require(Appearance.fromId("swiss") == Appearance.SWISS, "Swiss must round trip through preferences");
        for (Appearance appearance : Appearance.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                Appearance.Palette colors = appearance.palette(dark);
                require(contrast(colors.ink(), colors.bg()) >= 7, "Body contrast");
                require(contrast(colors.muted(), colors.bg()) >= 4.5, "Secondary text contrast");
                require(contrast(dark ? colors.bg() : 0xffffffff, colors.accent()) >= 4.5,
                        "Primary control label contrast");
                if (appearance == Appearance.SWISS) require(contrast(colors.outline(), colors.card()) >= 3,
                        "Input outline must be distinguishable from the field");
            }
        }
        System.out.println("PASS persistent theme IDs and light/dark text contrast");
    }

    private static double contrast(int a, int b) {
        double m = luminance(a), n = luminance(b);
        return (Math.max(m, n) + .05) / (Math.min(m, n) + .05);
    }

    private static double luminance(int argb) {
        return .2126 * linear(argb >> 16 & 255) + .7152 * linear(argb >> 8 & 255) + .0722 * linear(argb & 255);
    }

    private static double linear(int value) {
        double channel = value / 255.0;
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
