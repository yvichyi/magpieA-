package ai.magpie.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Streams an authenticated ledger export over the same origin. */
final class LedgerCsv {
    @FunctionalInterface
    interface Target {
        OutputStream open() throws IOException;
    }

    static void copy(String origin, String key, String path, Target target, BooleanSupplier valid)
            throws IOException {
        if (!path.startsWith("/api/usage/requests.csv?") || path.contains("..") || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Invalid ledger export path");
        }
        String address = CoreConfig.remote(origin);
        checkScope(valid);
        HttpURLConnection auth = connection(address + "/?k=" + LedgerQuery.encode(key), 6000);
        String cookie;
        try {
            int status = auth.getResponseCode();
            String header = auth.getHeaderField("Set-Cookie");
            if ((status != 302 && status != 303) || header == null) {
                throw new IOException("管理接口认证失败");
            }
            cookie = header.split(";", 2)[0];
        } finally {
            auth.disconnect();
        }

        checkScope(valid);
        HttpURLConnection request = connection(address + path, 60000);
        request.setRequestProperty("Cookie", cookie);
        request.setRequestProperty("Accept-Encoding", "identity");
        try {
            int status = request.getResponseCode();
            String type = request.getContentType();
            if (status != 200 || type == null || !type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("text/csv")) {
                throw new IOException("导出接口没有返回 CSV");
            }
            checkScope(valid);
            long copied = 0;
            try (InputStream in = request.getInputStream(); OutputStream out = target.open()) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    checkScope(valid);
                    out.write(buffer, 0, count);
                    copied += count;
                }
                checkScope(valid);
                long expected = request.getContentLengthLong();
                if (expected >= 0 && expected != copied) throw new IOException("CSV 下载不完整");
            }
        } finally {
            request.disconnect();
        }
    }

    private static HttpURLConnection connection(String url, int readTimeout) throws IOException {
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setConnectTimeout(4000);
        request.setReadTimeout(readTimeout);
        request.setInstanceFollowRedirects(false);
        return request;
    }

    private static void checkScope(BooleanSupplier valid) throws IOException {
        if (!valid.getAsBoolean()) throw new IOException("工作空间已变化");
    }
}
