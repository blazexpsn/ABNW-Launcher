package org.teamzetaverse.launcher.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teamzetaverse.launcher.LauncherConfig;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Hashing;
import org.teamzetaverse.launcher.util.Json;
import org.teamzetaverse.launcher.util.OperatingSystem;

public final class GameInstaller {
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";

    private final LauncherPaths paths;
    private final LauncherConfig config;

    public GameInstaller(final LauncherPaths paths, final LauncherConfig config) {
        this.paths = paths;
        this.config = config;
    }

    public InstalledGame install(final Release release, final String librariesJson, final Progress progress) throws IOException, Progress.CancelledException {
        if (!release.isResolved()) {
            throw new IOException(release.displayName() + " has not been resolved against its release manifest.");
        }
        String mc = release.minecraft;
        Path versionDir = this.paths.versions().resolve(Release.safe(mc));

        progress.stage("Fetching Minecraft " + mc + " metadata...", 0);
        Path versionJsonPath = versionDir.resolve(Release.safe(mc) + ".json");
        JsonObject versionJson = this.versionJson(mc, versionJsonPath);
        progress.checkCancelled();

        JsonObject client = Json.object(Json.object(versionJson, "downloads"), "client");
        Path clientJar = versionDir.resolve(Release.safe(mc) + ".jar");
        if (!Hashing.matches(clientJar, "SHA-1", Json.string(client, "sha1"), Json.number(client, "size", -1))) {
            progress.stage("Downloading Minecraft " + mc, Json.number(client, "size", -1));
            Http.download(Json.string(client, "url"), clientJar, Json.string(client, "sha1"), progress::advance);
        }
        progress.checkCancelled();

        Map<String, Path> libraries = new LinkedHashMap<>();
        Downloads libraryDownloads = new Downloads();
        this.mojangLibraries(versionJson, libraries, libraryDownloads);
        this.releaseLibraries(librariesJson, libraries, libraryDownloads, progress);
        libraryDownloads.run("Downloading libraries", progress);

        JsonObject assetIndexRef = Json.object(versionJson, "assetIndex");
        String assetIndexId = Json.string(assetIndexRef, "id");
        Path indexPath = this.paths.assets().resolve("indexes").resolve(Release.safe(assetIndexId) + ".json");
        if (!Hashing.matches(indexPath, "SHA-1", Json.string(assetIndexRef, "sha1"), -1)) {
            Http.download(Json.string(assetIndexRef, "url"), indexPath, Json.string(assetIndexRef, "sha1"), null);
        }
        JsonObject objects = Json.object(Json.readObject(indexPath), "objects");
        Downloads assetDownloads = new Downloads();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (objects != null) {
            for (Map.Entry<String, JsonElement> object : objects.entrySet()) {
                JsonObject info = object.getValue().getAsJsonObject();
                String hash = Json.string(info, "hash");
                if (hash == null || hash.length() < 2 || !seen.add(hash)) {
                    continue;
                }
                String relative = hash.substring(0, 2) + "/" + hash;
                assetDownloads.add(RESOURCES_URL + relative, this.paths.assets().resolve("objects").resolve(relative), hash, Json.number(info, "size", -1));
            }
        }
        assetDownloads.run("Downloading assets", progress);

        String loggingArgument = null;
        JsonObject logging = Json.object(Json.object(versionJson, "logging"), "client");
        if (logging != null) {
            JsonObject file = Json.object(logging, "file");
            Path config = this.paths.assets().resolve("log_configs").resolve(Release.safe(Json.string(file, "id")));
            if (!Hashing.matches(config, "SHA-1", Json.string(file, "sha1"), -1)) {
                Http.download(Json.string(file, "url"), config, Json.string(file, "sha1"), null);
            }
            String argument = Json.string(logging, "argument");
            if (argument != null) {
                loggingArgument = argument.replace("${path}", config.toString());
            }
        }

        Path java = this.java(versionJson, progress);
        progress.checkCancelled();

        Path abnwJar = AbnwPatcher.ensure(release, clientJar, this.paths.deltas(), this.paths.patchedJars(), progress);

        List<Path> classpath = new ArrayList<>(libraries.values());
        classpath.add(abnwJar);
        return new InstalledGame(versionJson, classpath, this.paths.libraries(), this.paths.assets(), assetIndexId, java, loggingArgument);
    }

    private JsonObject versionJson(final String mc, final Path path) throws IOException {
        JsonObject manifest = Http.getJson(VERSION_MANIFEST_URL);
        JsonArray versions = manifest.getAsJsonArray("versions");
        JsonObject entry = null;
        if (versions != null) {
            for (JsonElement version : versions) {
                if (mc.equals(Json.string(version.getAsJsonObject(), "id"))) {
                    entry = version.getAsJsonObject();
                    break;
                }
            }
        }
        if (entry == null) {
            throw new IOException("Mojang has no Minecraft " + mc + ".");
        }
        String sha1 = Json.string(entry, "sha1");
        if (!Hashing.matches(path, "SHA-1", sha1, -1)) {
            Http.download(Json.string(entry, "url"), path, sha1, null);
        }
        return Json.readObject(path);
    }

    private void mojangLibraries(final JsonObject versionJson, final Map<String, Path> libraries, final Downloads downloads) throws IOException {
        JsonArray list = versionJson.getAsJsonArray("libraries");
        if (list == null) {
            return;
        }
        for (JsonElement element : list) {
            JsonObject library = element.getAsJsonObject();
            if (!Rules.allowed(library.getAsJsonArray("rules"), Map.of())) {
                continue;
            }
            JsonObject artifact = Json.object(Json.object(library, "downloads"), "artifact");
            if (artifact == null) {
                continue;
            }
            Path target = this.libraryPath(Json.string(artifact, "path"));
            downloads.add(Json.string(artifact, "url"), target, Json.string(artifact, "sha1"), Json.number(artifact, "size", -1));
            libraries.put(artifactKey(Json.string(library, "name")), target);
        }
    }

    private void releaseLibraries(final String librariesJson, final Map<String, Path> libraries, final Downloads downloads, final Progress progress)
        throws IOException, Progress.CancelledException {
        JsonArray list = Json.parseObject(librariesJson).getAsJsonArray("libraries");
        if (list == null) {
            return;
        }
        progress.stage("Resolving ABNW libraries...", 0);
        for (JsonElement element : list) {
            progress.checkCancelled();
            JsonObject library = element.getAsJsonObject();
            String name = Json.string(library, "name");
            String repository = Json.string(library, "url");
            if (name == null || repository == null) {
                continue;
            }
            String[] parts = name.split(":");
            if (parts.length < 3) {
                continue;
            }
            String classifier = parts.length > 3 ? parts[3] : null;
            JsonObject natives = Json.object(library, "natives");
            if (natives != null) {
                String key = OperatingSystem.CURRENT.mojangName + (OperatingSystem.isArm64() ? "-arm64" : "");
                classifier = Json.string(natives, key);
                if (classifier == null) {
                    continue;
                }
            }
            String relative = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2]
                + (classifier == null ? "" : "-" + classifier) + ".jar";
            String url = (repository.endsWith("/") ? repository : repository + "/") + relative;
            Path target = this.libraryPath(relative);
            String sha1 = null;
            if (!Files.isRegularFile(target)) {
                try {
                    sha1 = Http.getString(url + ".sha1").trim().split("\\s+")[0];
                } catch (IOException ignored) {
                }
            }
            downloads.add(url, target, sha1, -1);
            String group = parts[0] + ":" + parts[1] + (classifier == null ? "" : ":" + classifier);
            libraries.remove(group);
            libraries.put(group, target);
        }
    }

    private Path libraryPath(final String relative) throws IOException {
        Path root = this.paths.libraries();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("library path escapes the libraries folder: " + relative);
        }
        return target;
    }

    private static String artifactKey(final String name) {
        String[] parts = name == null ? new String[0] : name.split(":");
        if (parts.length < 3) {
            return String.valueOf(name);
        }
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }

    private Path java(final JsonObject versionJson, final Progress progress) throws IOException, Progress.CancelledException {
        if (this.config.javaPath != null && !this.config.javaPath.isBlank()) {
            Path configured = Path.of(this.config.javaPath.trim());
            if (!Files.isRegularFile(configured)) {
                throw new IOException("The Java path in Settings does not exist: " + configured);
            }
            return configured;
        }
        JsonObject javaVersion = Json.object(versionJson, "javaVersion");
        String component = Json.string(javaVersion, "component");
        if (component == null) {
            throw new IOException("Minecraft does not say which Java it needs. Set a Java path in Settings.");
        }
        return JavaRuntimes.ensure(this.paths.runtimes(), component, progress);
    }
}
