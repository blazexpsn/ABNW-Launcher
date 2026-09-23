package org.teamzetaverse.cosmetics.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

public final class CosmeticsService {
    public static final String DEFAULT_URL = "https://abnw-cosmetics.blazexpsn.workers.dev";
    public static final String URL_PROPERTY = "abnw.cosmeticsApiUrl";

    private static final Pattern UUID = Pattern.compile("^[0-9a-f]{32}$");
    private static final long MAX_JSON_BYTES = 1024L * 1024;

    private final String baseUrl;
    private final String userAgent;
    private final HttpClient http;

    public CosmeticsService(final String baseUrl, final String userAgent) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        boolean local = trimmed.startsWith("http://localhost:") || trimmed.startsWith("http://127.0.0.1:");
        if (!trimmed.startsWith("https://") && !local) {
            throw new IllegalArgumentException("The cosmetics service must use https: " + baseUrl);
        }
        this.baseUrl = trimmed;
        this.userAgent = userAgent;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    }

    public static CosmeticsService defaults(final String userAgent) {
        String configured = System.getProperty(URL_PROPERTY);
        return new CosmeticsService(configured == null || configured.isBlank() ? DEFAULT_URL : configured.trim(), userAgent);
    }

    public String baseUrl() {
        return this.baseUrl;
    }

    public String assetUrl(final Cosmetic cosmetic, final CosmeticAsset asset) {
        return this.baseUrl + "/v1/assets/" + cosmetic.id().replace(':', '/') + "/" + asset.name() + "?sha256=" + asset.sha256();
    }

    public CosmeticRegistry fetchRegistry() throws IOException {
        return CosmeticRegistry.parse(new String(this.get(this.baseUrl + "/v1/cosmetics/registry", MAX_JSON_BYTES), StandardCharsets.UTF_8));
    }

    /** Local content metadata is never proof of ownership. */
    public CosmeticRegistry cachedRegistry(final Path cache) throws IOException {
        Path file = cache.resolve("registry.json");
        if (Files.size(file) > MAX_JSON_BYTES) throw new IOException("Cached cosmetic registry is too large.");
        return CosmeticRegistry.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public CosmeticRegistry synchronizeRegistry(final Path cache) throws IOException {
        byte[] bytes = this.get(this.baseUrl + "/v1/cosmetics/registry", MAX_JSON_BYTES);
        CosmeticRegistry registry = CosmeticRegistry.parse(new String(bytes, StandardCharsets.UTF_8));
        Files.createDirectories(cache);
        Path temp = Files.createTempFile(cache, "registry-", ".part");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, cache.resolve("registry.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, cache.resolve("registry.json"), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        return registry;
    }

    public java.util.Optional<Path> cachedAsset(final CosmeticAsset asset, final Path cache) throws IOException {
        Path target = cache.resolve(asset.sha256().substring(0, 2)).resolve(asset.sha256() + "." + asset.extension());
        return Files.isRegularFile(target) && Files.size(target) == asset.size() && sha256(target).equals(asset.sha256())
            ? java.util.Optional.of(target) : java.util.Optional.empty();
    }

    public CosmeticProfile fetchProfile(final String uuid) throws IOException {
        String compact = uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if (!UUID.matcher(compact).matches()) {
            throw new IllegalArgumentException("Not a Minecraft UUID: " + uuid);
        }
        JsonObject body;
        try {
            body = JsonParser.parseString(new String(this.get(this.baseUrl + "/v1/profiles/" + compact, MAX_JSON_BYTES), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("The cosmetics service sent an unreadable profile.", e);
        }
        List<String> cosmetics = new ArrayList<>();
        JsonElement list = body.get("cosmetics");
        if (list instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    cosmetics.add(element.getAsString());
                }
            }
        }
        JsonElement patron = body.get("patron");
        return new CosmeticProfile(compact, patron != null && patron.isJsonPrimitive() && patron.getAsBoolean(), List.copyOf(cosmetics));
    }

    public Path download(final Cosmetic cosmetic, final CosmeticAsset asset, final Path cacheDirectory) throws IOException {
        Path target = cacheDirectory.resolve(asset.sha256().substring(0, 2)).resolve(asset.sha256() + "." + asset.extension());
        if (Files.isRegularFile(target) && Files.size(target) == asset.size() && sha256(target).equals(asset.sha256())) {
            return target;
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), asset.sha256() + "-", ".part");
        try {
            HttpResponse<InputStream> response = this.send(this.assetUrl(cosmetic, asset));
            MessageDigest digest = digest();
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > asset.size()) {
                        throw new IOException(cosmetic.id() + " " + asset.name() + " is larger than the registry says.");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (total != asset.size() || !actual.equals(asset.sha256())) {
                throw new IOException(cosmetic.id() + " " + asset.name() + " does not match the registry checksum.");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private byte[] get(final String url, final long maxBytes) throws IOException {
        HttpResponse<InputStream> response = this.send(url);
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes((int)Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
            if (bytes.length > maxBytes) {
                throw new IOException(url + " is larger than " + maxBytes + " bytes.");
            }
            return bytes;
        }
    }

    private HttpResponse<InputStream> send(final String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", this.userAgent).GET().build();
        try {
            HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("The cosmetics service answered HTTP " + response.statusCode() + " for " + url);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the cosmetics service.", e);
        }
    }

    private static String sha256(final Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
