package org.teamzetaverse.launcher.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.lwjgl.system.MemoryUtil;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.FileMoves;

final class PlayerHead {
    static final String SCHEME = "head:";
    static final int SIZE = 20;

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{32}");
    private static final Duration FRESH = Duration.ofDays(1);
    private static final long MAX_SKIN_BYTES = 2L * 1024 * 1024;
    private static final int OUTLINE = 0xFF2E081E;

    private static final double[] VIEW = normalize(0.82, 0.78, 1.0);
    private static final double[] RIGHT = normalize(VIEW[2], 0.0, -VIEW[0]);
    private static final double[] UP = cross(RIGHT, new double[] {-VIEW[0], -VIEW[1], -VIEW[2]});

    private PlayerHead() {
    }

    static String key(final String uuid) {
        String id = normalizeUuid(uuid);
        return id == null ? null : SCHEME + id;
    }

    static String normalizeUuid(final String uuid) {
        if (uuid == null) {
            return null;
        }
        String id = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return UUID.matcher(id).matches() ? id : null;
    }

    static byte[] skin(final Path directory, final String key) throws IOException {
        String id = normalizeUuid(key.substring(SCHEME.length()));
        if (id == null) {
            throw new IOException("not a player id");
        }
        Path cached = directory.resolve(id + ".png");
        boolean present = Files.isRegularFile(cached);
        if (present && Files.getLastModifiedTime(cached).toInstant().isAfter(Instant.now().minus(FRESH))) {
            return Files.readAllBytes(cached);
        }
        try {
            byte[] bytes = Http.getBytes(skinUrl(id), MAX_SKIN_BYTES);
            Files.createDirectories(directory);
            Path temp = cached.resolveSibling(cached.getFileName() + ".part");
            Files.write(temp, bytes);
            FileMoves.replace(temp, cached);
            return bytes;
        } catch (IOException | RuntimeException e) {
            if (present) {
                return Files.readAllBytes(cached);
            }
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }

    private static String skinUrl(final String id) throws IOException {
        JsonObject profile = Http.getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        if (profile == null || !profile.has("properties")) {
            throw new IOException("profile has no textures");
        }
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                throw new IOException("player uses the default skin");
            }
            String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
            if (url.startsWith("http://")) {
                url = "https://" + url.substring("http://".length());
            }
            if (!url.startsWith("https://textures.minecraft.net/")) {
                throw new IOException("unexpected skin host");
            }
            return url;
        }
        throw new IOException("profile has no textures");
    }

    static ByteBuffer render(final ByteBuffer skin, final int width, final int height) throws IOException {
        if (width < 64 || width % 64 != 0 || (height != width && height != width / 2)) {
            throw new IOException("unexpected skin size " + width + "x" + height);
        }
        int texel = width / 64;
        boolean hat = !(height == width / 2 && hatIsSolid(skin, width, texel));
        double lo = -0.5;
        double hi = 8.5;
        double extent = 0;
        for (int corner = 0; corner < 8; corner++) {
            double[] p = {(corner & 1) == 0 ? lo : hi, (corner & 2) == 0 ? lo : hi, (corner & 4) == 0 ? lo : hi};
            double[] d = {p[0] - 4, p[1] - 4, p[2] - 4};
            extent = Math.max(extent, Math.max(Math.abs(dot(d, RIGHT)), Math.abs(dot(d, UP))));
        }
        double pixel = 2 * extent / (SIZE - 2);
        int[] out = new int[SIZE * SIZE];
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                double sx = (i + 0.5 - SIZE / 2.0) * pixel;
                double sy = (SIZE / 2.0 - (j + 0.5)) * pixel;
                double[] origin = {
                    4 + RIGHT[0] * sx + UP[0] * sy + VIEW[0] * 50,
                    4 + RIGHT[1] * sx + UP[1] * sy + VIEW[1] * 50,
                    4 + RIGHT[2] * sx + UP[2] * sy + VIEW[2] * 50};
                int colour = 0;
                if (hat) {
                    colour = sample(skin, width, texel, origin, -0.5, 8.5, 32);
                    if ((colour >>> 24) < 128) {
                        colour = 0;
                    }
                }
                if (colour == 0) {
                    colour = sample(skin, width, texel, origin, 0, 8, 0);
                }
                out[j * SIZE + i] = colour;
            }
        }
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int colour = out[j * SIZE + i];
                if (colour == 0 && (opaque(out, i - 1, j) || opaque(out, i + 1, j) || opaque(out, i, j - 1) || opaque(out, i, j + 1))) {
                    colour = OUTLINE;
                }
                pixels.put((byte)(colour >> 16)).put((byte)(colour >> 8)).put((byte)colour).put((byte)(colour == 0 ? 0 : 255));
            }
        }
        return pixels.flip();
    }

    private static boolean opaque(final int[] out, final int i, final int j) {
        return i >= 0 && j >= 0 && i < SIZE && j < SIZE && out[j * SIZE + i] != 0 && out[j * SIZE + i] != OUTLINE;
    }

    private static int sample(final ByteBuffer skin, final int width, final int texel, final double[] origin, final double lo, final double hi,
                              final int uOffset) {
        double best = Double.MAX_VALUE;
        int u = -1;
        int v = -1;
        float shade = 1f;
        double span = hi - lo;
        double t = (origin[2] - hi) / VIEW[2];
        double x = origin[0] - VIEW[0] * t;
        double y = origin[1] - VIEW[1] * t;
        if (x >= lo && x <= hi && y >= lo && y <= hi && t < best) {
            best = t;
            u = 8 + cell(x, lo, span);
            v = 8 + 7 - cell(y, lo, span);
            shade = 0.9f;
        }
        t = (origin[0] - hi) / VIEW[0];
        double z = origin[2] - VIEW[2] * t;
        y = origin[1] - VIEW[1] * t;
        if (z >= lo && z <= hi && y >= lo && y <= hi && t < best) {
            best = t;
            u = 16 + 7 - cell(z, lo, span);
            v = 8 + 7 - cell(y, lo, span);
            shade = 0.7f;
        }
        t = (origin[1] - hi) / VIEW[1];
        x = origin[0] - VIEW[0] * t;
        z = origin[2] - VIEW[2] * t;
        if (x >= lo && x <= hi && z >= lo && z <= hi && t < best) {
            u = 8 + cell(x, lo, span);
            v = cell(z, lo, span);
            shade = 1f;
        }
        if (u < 0) {
            return 0;
        }
        int index = (((v * texel + texel / 2) * width) + (u + uOffset) * texel + texel / 2) * 4;
        int a = skin.get(index + 3) & 0xFF;
        if (a == 0) {
            return 0;
        }
        int r = Math.round((skin.get(index) & 0xFF) * shade);
        int g = Math.round((skin.get(index + 1) & 0xFF) * shade);
        int b = Math.round((skin.get(index + 2) & 0xFF) * shade);
        return (Math.max(a, 1) << 24) | (r << 16) | (g << 8) | Math.max(b, 1);
    }

    private static int cell(final double value, final double lo, final double span) {
        return Math.min(7, Math.max(0, (int)Math.floor((value - lo) / span * 8)));
    }

    private static boolean hatIsSolid(final ByteBuffer skin, final int width, final int texel) {
        int first = skin.getInt(((8 * texel) * width + 40 * texel) * 4);
        for (int v = 0; v < 16 * texel; v++) {
            for (int u = 32 * texel; u < 64 * texel; u++) {
                if (v < 8 * texel && (u < 40 * texel || u >= 56 * texel)) {
                    continue;
                }
                int index = (v * width + u) * 4;
                if ((skin.get(index + 3) & 0xFF) != 255 || skin.getInt(index) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double[] normalize(final double x, final double y, final double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    private static double[] cross(final double[] a, final double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(final double[] a, final double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
