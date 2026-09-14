package org.teamzetaverse.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static JsonObject parseObject(final String text) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new IOException("expected a JSON object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("invalid JSON: " + e.getMessage(), e);
        }
    }

    public static JsonObject readObject(final Path file) throws IOException {
        return parseObject(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static <T> T read(final Path file, final Class<T> type) throws IOException {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (RuntimeException e) {
            throw new IOException("invalid " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static void write(final Path file, final Object value) throws IOException {
        writeString(file, GSON.toJson(value));
    }

    public static void writeString(final Path file, final String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String string(final JsonObject object, final String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    public static long number(final JsonObject object, final String key, final long fallback) {
        JsonElement element = object == null ? null : object.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsLong();
    }

    public static JsonObject object(final JsonObject object, final String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }
}
