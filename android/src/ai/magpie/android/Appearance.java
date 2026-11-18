package ai.magpie.android;

enum Appearance {
    GLASS("glass", "液态玻璃",
            new Palette(0xfff3f5f7, 0xffffffff, 0xff1a2027, 0xff58646e, 0xffd7dfe5, 0xff1a2027, 0xffe9edf2, 0xffe0e8ee, 0xffd7dfe5),
            new Palette(0xff11151a, 0xff181d23, 0xffe9edf2, 0xffa3aab0, 0xff37434c, 0xffe9edf2, 0xff252e37, 0xff293944, 0xff37434c)),
    SWISS("swiss", "瑞士风格",
            new Palette(0xfff4f2eb, 0xfffffdf6, 0xff1c1b17, 0xff575650, 0xffbcb9ad, 0xffab251e, 0xffeae7de, 0xfff1dbd5, 0xff817c70),
            new Palette(0xff1c1b17, 0xff27251f, 0xfff4f2eb, 0xffbbb7aa, 0xff575249, 0xffff8477, 0xff302e27, 0xff512e28, 0xff938a77));

    record Palette(int bg, int card, int ink, int muted, int line, int accent, int soft, int accentSoft, int outline) {}

    final String id, label;
    private final Palette light, dark;

    Appearance(String id, String label, Palette light, Palette dark) {
        this.id = id;
        this.label = label;
        this.light = light;
        this.dark = dark;
    }

    Palette palette(boolean night) { return night ? dark : light; }

    static Appearance fromId(String id) {
        return SWISS.id.equals(id) ? SWISS : GLASS;
    }
}
