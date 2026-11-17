package ai.magpie.android;

public final class CoreConfigTest {
    private static void equal(Object actual, Object expected) {
        if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
    }
    private static void reject(String address) {
        try { CoreConfig.remote(address); throw new AssertionError("accepted " + address); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        equal(CoreConfig.remote("https://example.com/"), "https://example.com");
        equal(CoreConfig.remote("http://192.168.1.12:3430"), "http://192.168.1.12:3430");
        equal(CoreConfig.remote("http://127.0.0.1:3430/"), "http://127.0.0.1:3430");
        equal(CoreConfig.remote("http://10.0.2.2:3430"), "http://10.0.2.2:3430");
        for (String s : new String[]{"https://example.com/?k=secret", "javascript:alert(1)", "file:///data", "http://example.com", "http://192.168.1.2.evil.com", "https://user:pass@example.com", "https://example.com/#x", "http://172.32.0.1", "http://999.1.1.1", "https://example.com:99999", "http://127.1.evil"}) reject(s);
        String key = CoreConfig.newKey();
        if (!key.matches("[a-f0-9]{64}") || key.equals(CoreConfig.newKey())) throw new AssertionError("weak key");
        if (!CoreConfig.sameOrigin("http://127.0.0.1:3430/a", "http://127.0.0.1:3430")) throw new AssertionError("own origin");
        if (CoreConfig.sameOrigin("http://127.0.0.1:3430.evil.com/", "http://127.0.0.1:3430")) throw new AssertionError("host confusion");
        if (CoreConfig.sameOrigin("https://example.com:444/", "https://example.com")) throw new AssertionError("port confusion");
        equal(CoreConfig.safeFilename("../../backup.json"), "backup.json");
        equal(CoreConfig.chromiumMajor("Mozilla/5.0 Android HuaweiBrowser/15.0 Chrome/120.0.0.0 Mobile"),120);
        equal(CoreConfig.chromiumMajor("Mozilla/5.0 Android Chrome/83.0.4103.120"),83);
        equal(CoreConfig.chromiumMajor("vendor engine with an unknown UA"),0);
        equal(CoreConfig.boundOrigin("● magpie web on http://127.0.0.1:45678/?k=run-key", "run-key"), "http://127.0.0.1:45678");
        equal(CoreConfig.boundOrigin("● magpie web on http://127.0.0.1:45678/?k=other", "run-key"), "");
        equal(CoreConfig.boundOrigin("● magpie web on http://evil.test:45678/?k=run-key", "run-key"), "");
        equal(CoreConfig.boundOrigin("untrusted prefix ● magpie web on http://127.0.0.1:45678/?k=run-key", "run-key"), "");
        System.out.println("CoreConfig: validation, origin isolation, random keys and filenames passed");
    }
}
