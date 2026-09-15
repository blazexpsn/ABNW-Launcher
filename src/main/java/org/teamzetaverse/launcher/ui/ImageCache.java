package org.teamzetaverse.launcher.ui;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Hashing;

final class ImageCache {
    record Texture(int id, int width, int height) {
        float aspect() {
            return this.height == 0 ? 1f : this.width / (float)this.height;
        }
    }

    private enum State {
        LOADING, READY, FAILED
    }

    private static final class Entry {
        State state = State.LOADING;
        Texture texture;
        long lastUsedFrame;
        boolean pixelated;
    }

    private record Decoded(String key, ByteBuffer pixels, int width, int height, boolean stbOwned) {
        void free() {
            if (this.stbOwned) {
                STBImage.stbi_image_free(this.pixels);
            } else {
                MemoryUtil.memFree(this.pixels);
            }
        }
    }

    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 8192L * 8192L;
    private static final int MAX_READY = 28;

    private final Path diskCache;
    private final Map<String, Entry> entries = new HashMap<>();
    private final ConcurrentLinkedQueue<Decoded> uploads = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final ExecutorService decoder = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ABNW image loader");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private long frame;

    ImageCache(final Path diskCache) {
        this.diskCache = diskCache;
    }

    Texture get(final String key, final int maxDimension) {
        return this.get(key, maxDimension, false);
    }

    Texture get(final String key, final int maxDimension, final boolean pixelated) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        Entry entry = this.entries.get(key);
        if (entry == null) {
            entry = new Entry();
            entry.pixelated = pixelated;
            this.entries.put(key, entry);
            this.decoder.execute(() -> this.decode(key, maxDimension));
        }
        entry.lastUsedFrame = this.frame;
        return entry.state == State.READY ? entry.texture : null;
    }

    boolean isLoading() {
        return !this.uploads.isEmpty() || this.entries.values().stream().anyMatch(e -> e.state == State.LOADING);
    }

    boolean hasFailed(final String key) {
        Entry entry = this.entries.get(key);
        return entry != null && entry.state == State.FAILED;
    }

    void beginFrame() {
        this.frame++;
        String failed;
        while ((failed = this.failures.poll()) != null) {
            Entry entry = this.entries.get(failed);
            if (entry != null) {
                entry.state = State.FAILED;
            }
        }
        int budget = 2;
        Decoded decoded;
        while (budget-- > 0 && (decoded = this.uploads.poll()) != null) {
            Entry entry = this.entries.get(decoded.key());
            try {
                if (entry != null && entry.state == State.LOADING) {
                    entry.texture = upload(decoded, entry.pixelated);
                    entry.state = State.READY;
                }
            } finally {
                decoded.free();
            }
        }
        this.evict();
    }

    private void evict() {
        long ready = this.entries.values().stream().filter(e -> e.state == State.READY).count();
        if (ready <= MAX_READY) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = this.entries.entrySet().stream()
            .filter(e -> e.getValue().state == State.READY && e.getValue().lastUsedFrame < this.frame - 120)
            .sorted((a, b) -> Long.compare(a.getValue().lastUsedFrame, b.getValue().lastUsedFrame))
            .toList().iterator();
        while (ready > MAX_READY && iterator.hasNext()) {
            Map.Entry<String, Entry> victim = iterator.next();
            glDeleteTextures(victim.getValue().texture.id());
            this.entries.remove(victim.getKey());
            ready--;
        }
    }

    private void decode(final String key, final int maxDimension) {
        try {
            byte[] bytes = this.fetch(key);
            ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
            try {
                file.put(bytes).flip();
                Decoded decoded = decodeImage(key, file, maxDimension);
                this.uploads.add(decoded);
            } finally {
                MemoryUtil.memFree(file);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not load image " + key + ": " + e.getMessage());
            this.failures.add(key);
        }
    }

    private byte[] fetch(final String key) throws IOException {
        if (key.startsWith("resource:")) {
            try (InputStream in = ImageCache.class.getResourceAsStream(key.substring("resource:".length()))) {
                if (in == null) {
                    throw new IOException("missing resource");
                }
                return in.readAllBytes();
            }
        }
        if (key.startsWith("file:")) {
            Path path = Path.of(key.substring("file:".length()));
            if (Files.size(path) > MAX_BYTES) {
                throw new IOException("image is too large");
            }
            return Files.readAllBytes(path);
        }
        if (key.startsWith("https://")) {
            Path cached = this.diskCache.resolve(Hashing.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (Files.isRegularFile(cached)) {
                return Files.readAllBytes(cached);
            }
            byte[] bytes = Http.getBytes(key, MAX_BYTES);
            try {
                Files.createDirectories(this.diskCache);
                Path temp = cached.resolveSibling(cached.getFileName() + ".part");
                Files.write(temp, bytes);
                org.teamzetaverse.launcher.util.FileMoves.replace(temp, cached);
            } catch (IOException e) {
                System.err.println("Could not cache image " + key + ": " + e.getMessage());
            }
            return bytes;
        }
        throw new IOException("unsupported image source");
    }

    private static Decoded decodeImage(final String key, final ByteBuffer file, final int maxDimension) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(file, w, h, channels)) {
                throw new IOException(STBImage.stbi_failure_reason());
            }
            long pixelCount = (long)w.get(0) * (long)h.get(0);
            if (w.get(0) <= 0 || h.get(0) <= 0 || w.get(0) > MAX_DIMENSION || h.get(0) > MAX_DIMENSION || pixelCount > MAX_PIXELS) {
                throw new IOException("image is " + w.get(0) + "x" + h.get(0) + ", beyond the " + MAX_DIMENSION + "px / " + MAX_PIXELS + " pixel limit");
            }
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
            if (pixels == null) {
                throw new IOException(STBImage.stbi_failure_reason());
            }
            int width = w.get(0);
            int height = h.get(0);
            int longest = Math.max(width, height);
            if (maxDimension > 0 && longest > maxDimension) {
                float factor = maxDimension / (float)longest;
                int newWidth = Math.max(1, Math.round(width * factor));
                int newHeight = Math.max(1, Math.round(height * factor));
                ByteBuffer resized = MemoryUtil.memAlloc(newWidth * newHeight * 4);
                ByteBuffer result = STBImageResize.stbir_resize_uint8_srgb(pixels, width, height, width * 4, resized, newWidth, newHeight, newWidth * 4,
                    STBImageResize.STBIR_RGBA);
                STBImage.stbi_image_free(pixels);
                if (result == null) {
                    MemoryUtil.memFree(resized);
                    throw new IOException("could not resize");
                }
                return new Decoded(key, resized, newWidth, newHeight, false);
            }
            return new Decoded(key, pixels, width, height, true);
        }
    }

    private static Texture upload(final Decoded decoded, final boolean pixelated) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, decoded.width(), decoded.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, decoded.pixels());
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, pixelated ? GL_NEAREST : GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        return new Texture(id, decoded.width(), decoded.height());
    }

    void dispose() {
        this.decoder.shutdownNow();
        for (Entry entry : this.entries.values()) {
            if (entry.state == State.READY) {
                glDeleteTextures(entry.texture.id());
            }
        }
        this.entries.clear();
        Decoded decoded;
        while ((decoded = this.uploads.poll()) != null) {
            decoded.free();
        }
    }
}
