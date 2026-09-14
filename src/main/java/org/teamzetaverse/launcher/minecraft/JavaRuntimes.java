package org.teamzetaverse.launcher.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Json;
import org.teamzetaverse.launcher.util.OperatingSystem;

final class JavaRuntimes {
    private static final String INDEX_URL =
        "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private JavaRuntimes() {
    }

    static Path ensure(final Path runtimesDir, final String component, final Progress progress) throws IOException, Progress.CancelledException {
        Path home = runtimesDir.resolve(component);
        Path marker = runtimesDir.resolve(component + ".sha1");

        progress.status("Checking Java (" + component + ")...");
        JsonObject index = Http.getJson(INDEX_URL);
        JsonObject platform = Json.object(index, OperatingSystem.CURRENT.javaRuntimePlatform());
        JsonArray builds = platform == null ? null : platform.getAsJsonArray(component);
        if (builds == null || builds.isEmpty()) {
            throw new IOException("Mojang has no " + component + " Java runtime for " + OperatingSystem.CURRENT.javaRuntimePlatform()
                + ". Set a Java path in Settings.");
        }
        JsonObject manifestRef = Json.object(builds.get(0).getAsJsonObject(), "manifest");
        String manifestSha1 = Json.string(manifestRef, "sha1");

        Path existing = findJava(home);
        if (existing != null && Files.isRegularFile(marker) && Files.readString(marker, StandardCharsets.UTF_8).trim().equals(manifestSha1)) {
            return existing;
        }

        JsonObject manifest = Http.getJson(Json.string(manifestRef, "url"));
        JsonObject files = Json.object(manifest, "files");
        Downloads downloads = new Downloads();
        java.util.List<Path> executables = new java.util.ArrayList<>();
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            Path target = home.resolve(file.getKey()).normalize();
            if (!target.startsWith(home)) {
                throw new IOException("runtime manifest escapes its folder: " + file.getKey());
            }
            JsonObject info = file.getValue().getAsJsonObject();
            switch (String.valueOf(Json.string(info, "type"))) {
                case "directory" -> Files.createDirectories(target);
                case "file" -> {
                    JsonObject raw = Json.object(Json.object(info, "downloads"), "raw");
                    downloads.add(Json.string(raw, "url"), target, Json.string(raw, "sha1"), Json.number(raw, "size", -1));
                    if (info.has("executable") && info.get("executable").getAsBoolean()) {
                        executables.add(target);
                    }
                }
                default -> {
                }
            }
        }
        downloads.run("Downloading Java (" + component + ")", progress);
        for (Path executable : executables) {
            executable.toFile().setExecutable(true, false);
        }
        Path java = findJava(home);
        if (java == null) {
            throw new IOException("The downloaded Java runtime has no java executable.");
        }
        Files.writeString(marker, manifestSha1, StandardCharsets.UTF_8);
        return java;
    }

    static Path findJava(final Path home) throws IOException {
        if (!Files.isDirectory(home)) {
            return null;
        }
        String name = OperatingSystem.CURRENT.javaExecutableName();
        try (Stream<Path> walk = Files.walk(home, 6)) {
            return walk.filter(p -> p.getFileName().toString().equals(name) && p.getParent() != null
                    && p.getParent().getFileName().toString().equals("bin"))
                .findFirst()
                .orElse(null);
        }
    }
}
