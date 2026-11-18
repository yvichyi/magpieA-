package ai.magpie.android;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class LedgerCsvTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger foreignRequests = new AtomicInteger();
        int size = 9 * 1024 * 1024;
        byte[] data = new byte[size];
        java.util.Arrays.fill(data, (byte)'x');
        server.createContext("/", exchange -> {
            if (!"k=a%2Bb".equals(exchange.getRequestURI().getRawQuery())) {
                exchange.sendResponseHeaders(403, -1);
            } else {
                exchange.getResponseHeaders().add("Set-Cookie", "magpie_web_3430=abc; HttpOnly");
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(303, -1);
            }
            exchange.close();
        });
        server.createContext("/api/usage/requests.csv", exchange -> {
            if (!"magpie_web_3430=abc".equals(exchange.getRequestHeaders().getFirst("Cookie"))) {
                exchange.sendResponseHeaders(403, -1);
            } else if ("redirect=1".equals(exchange.getRequestURI().getRawQuery())) {
                exchange.getResponseHeaders().add("Location", "/unexpected");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "text/csv; charset=utf-8");
                exchange.sendResponseHeaders(200, size);
                try (var out = exchange.getResponseBody()) {
                    out.write(data);
                } catch (IOException canceled) {
                    // The canceled export closes its connection.
                }
            }
            exchange.close();
        });
        server.createContext("/unexpected", exchange -> {
            foreignRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            String query = new LedgerQuery().with("q", "模型").csv("UTC");
            LedgerCsv.copy(origin, "a+b", query, () -> bytes, () -> true);
            if (bytes.size() != size || bytes.toByteArray()[size - 1] != 'x') {
                throw new AssertionError("large CSV was capped or truncated");
            }
            try {
                LedgerCsv.copy(origin, "a+b", "/api/usage/requests.csv?redirect=1",
                        () -> { throw new AssertionError("opened destination after redirect"); }, () -> true);
                throw new AssertionError("redirect accepted");
            } catch (IOException expected) {
                if (foreignRequests.get() != 0) throw new AssertionError("followed a redirect");
            }
            AtomicInteger checks = new AtomicInteger();
            try {
                LedgerCsv.copy(origin, "a+b", query, ByteArrayOutputStream::new,
                        () -> checks.incrementAndGet() < 4);
                throw new AssertionError("invalidated workspace continued writing");
            } catch (IOException expected) {
                if (checks.get() != 4) throw new AssertionError("incorrect cancel point");
            }
            System.out.println("LedgerCsvTest passed");
        } finally {
            server.stop(0);
        }
    }
}
