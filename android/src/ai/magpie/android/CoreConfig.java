package ai.magpie.android;

import java.net.URI;
import java.security.SecureRandom;

/** Pure Java validation shared by the Android host and the JVM tests. */
public final class CoreConfig {
    public static final String LOCAL = "http://127.0.0.1:3430";
    public static final String GATEWAY = "http://127.0.0.1:3425";
    private CoreConfig() { }

    public static String newKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b & 255));
        return out.toString();
    }

    public static String remote(String input) {
        try {
            URI u = new URI(input.trim());
            String scheme = u.getScheme(), host = u.getHost();
            if (host == null || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null
                    || u.getPort() > 65535 || u.getPort() == 0 || (!"".equals(u.getPath()) && !"/".equals(u.getPath()))) throw new Exception();
            if (!"https".equals(scheme) && !("http".equals(scheme) && privateHost(host))) throw new Exception();
            return scheme + "://" + u.getRawAuthority();
        } catch (Exception e) {
            throw new IllegalArgumentException("请输入 Magpie 管理页地址；公网使用 HTTPS，局域网可用 HTTP。地址不含路径、密钥或查询参数。");
        }
    }

    private static boolean privateHost(String host) {
        if ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host)) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) return false;
            n[i] = Integer.parseInt(parts[i]);
            if (n[i] > 255 || (parts[i].length() > 1 && parts[i].startsWith("0"))) return false;
        }
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] >= 16 && n[1] <= 31);
    }

    public static boolean sameOrigin(String address, String origin) {
        try {
            URI a = new URI(address), b = new URI(origin);
            return b.getScheme().equals(a.getScheme()) && b.getHost().equalsIgnoreCase(a.getHost())
                    && port(a) == port(b) && a.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    private static int port(URI u) { return u.getPort() >= 0 ? u.getPort() : ("https".equals(u.getScheme()) ? 443 : 80); }

    public static String safeFilename(String value) {
        String s = value.replace('\\', '/');
        s = s.substring(s.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return s.isEmpty() ? "magpie-export.bin" : s.substring(0, Math.min(160, s.length()));
    }

    public static int chromiumMajor(String userAgent) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:Chrome|Chromium)/([0-9]+)").matcher(userAgent==null?"":userAgent);
        if(!m.find())return 0;
        try{return Integer.parseInt(m.group(1));}catch(NumberFormatException e){return 0;}
    }

    /** Only the owned child's successful StartWeb line establishes a listener. */
    public static String boundOrigin(String line, String expectedKey) {
        String text = line.replaceAll("\u001B\\[[;\\d]*m", "").trim();
        String prefix = "● magpie web on ";
        if (!text.startsWith(prefix)) return "";
        try {
            URI u = new URI(text.substring(prefix.length()));
            if (!"http".equals(u.getScheme()) || !"127.0.0.1".equals(u.getHost()) || u.getPort() < 1024 || u.getPort() > 65535
                || !"/".equals(u.getPath()) || u.getRawFragment() != null || u.getUserInfo() != null
                || !("k=" + expectedKey).equals(u.getRawQuery())) return "";
            return "http://127.0.0.1:" + u.getPort();
        } catch (Exception e) { return ""; }
    }
}
