package org.teamzetaverse.launcher.net;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongConsumer;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.util.Json;

public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private static final String USER_AGENT = "ABNWLauncher/" + BuildInfo.VERSION;
    private static final int ATTEMPTS = 3;

    private Http() {
    }

    public static final class StatusException extends IOException {
        public final int status;
        public final String body;

        StatusException(final String url, final int status, final String body) {
            super("HTTP " + status + " from " + url);
            this.status = status;
            this.body = body;
        }
    }

    private static HttpRequest.Builder request(final String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", USER_AGENT);
    }

    private static String send(final HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new StatusException(request.uri().toString(), response.statusCode(), response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static String getString(final String url) throws IOException {
        return send(request(url).GET().build());
    }

    public static JsonObject getJson(final String url) throws IOException {
        return Json.parseObject(getString(url));
    }

    public static JsonObject getJson(final String url, final String bearerToken) throws IOException {
        return Json.parseObject(send(request(url).header("Authorization", "Bearer " + bearerToken).GET().build()));
    }

    public static JsonObject postJson(final String url, final JsonObject body) throws IOException {
        return Json.parseObject(send(request(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build()));
    }

    public static JsonObject postForm(final String url, final Map<String, String> form) throws IOException {
        StringJoiner joined = new StringJoiner("&");
        form.forEach((key, value) -> joined.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return Json.parseObject(send(request(url)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(joined.toString(), StandardCharsets.UTF_8))
            .build()));
    }

    public static void download(final String url, final Path target, final String expectedHash, final LongConsumer bytesRead) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                downloadOnce(url, target, expectedHash, bytesRead);
                return;
            } catch (StatusException e) {
                if (e.status == 404 || e.status == 403) {
                    throw e;
                }
                last = e;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private static void downloadOnce(final String url, final Path target, final String expectedHash, final LongConsumer bytesRead) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        String algorithm = expectedHash == null || expectedHash.isEmpty() ? null : expectedHash.length() == 40 ? "SHA-1" : "SHA-256";
        MessageDigest digest = null;
        if (algorithm != null) {
            try {
                digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            HttpResponse<InputStream> response = CLIENT.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new StatusException(url, response.statusCode(), "");
            }
            try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (digest != null) {
                        digest.update(buffer, 0, read);
                    }
                    if (bytesRead != null) {
                        bytesRead.accept(read);
                    }
                }
            }
            if (digest != null) {
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedHash)) {
                    throw new IOException("checksum mismatch for " + url + " (expected " + expectedHash + ", got " + actual + ")");
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
