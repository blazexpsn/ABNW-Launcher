package org.teamzetaverse.launcher.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Hashing;
import org.teamzetaverse.launcher.util.Json;

public final class ReleaseService {
    public record Listing(String latest, List<Release> releases) {
    }

    public record Resolved(Release release, String librariesJson) {
    }

    public Listing fetchListing() throws IOException {
        JsonObject root = Http.getJson(BuildInfo.versionsManifestUrl());
        if (Json.number(root, "formatVersion", 1) != 1) {
            throw new IOException("The ABNW versions manifest uses a newer format. Update the launcher.");
        }
        List<Release> releases = new ArrayList<>();
        JsonArray versions = root.getAsJsonArray("versions");
        if (versions != null) {
            for (JsonElement element : versions) {
                try {
                    Release release = Json.GSON.fromJson(element, Release.class);
                    checkListed(release);
                    releases.add(release);
                } catch (IOException | RuntimeException e) {
                    System.err.println("Skipping an ABNW release: " + e.getMessage());
                }
            }
        }
        return new Listing(Json.string(root, "latest"), releases);
    }

    public Resolved resolve(final Release listed) throws IOException {
        checkListed(listed);
        Release release = Json.GSON.fromJson(Json.GSON.toJson(listed), Release.class);

        String manifestText = fetchVerified(release.manifest);
        JsonObject manifest = Json.parseObject(manifestText);
        if (!release.id.equals(Json.string(manifest, "version")) || !release.minecraft.equals(Json.string(manifest, "minecraftVersion"))) {
            throw new IOException("The manifest linked for " + release.displayName() + " describes a different release.");
        }
        String format = Json.string(manifest, "format");
        if (format != null && !format.equals("vcdiff")) {
            throw new IOException(release.displayName() + " uses a patch format this launcher does not support.");
        }
        String deltaUrl = release.delta.url;
        release.delta = fileRef(Json.object(manifest, "delta"));
        release.delta.url = deltaUrl;
        release.source = fileRef(Json.object(manifest, "source"));
        release.target = fileRef(Json.object(manifest, "target"));
        if (!release.isResolved()) {
            throw new IOException("The manifest for " + release.displayName() + " is missing its hashes.");
        }

        String libraries = fetchVerified(release.libraries);
        JsonObject component = Json.parseObject(libraries);
        if (!"org.teamzetaverse.abnw.libraries".equals(Json.string(component, "uid"))) {
            throw new IOException("The libraries of " + release.displayName() + " are not an ABNW libraries component.");
        }
        return new Resolved(release, libraries);
    }

    private static Release.FileRef fileRef(final JsonObject object) {
        Release.FileRef ref = new Release.FileRef();
        if (object != null) {
            ref.size = Json.number(object, "size", -1);
            String sha = Json.string(object, "sha256");
            ref.sha256 = sha == null ? "" : sha.toLowerCase();
        }
        return ref;
    }

    private static String fetchVerified(final Release.FileRef ref) throws IOException {
        String text = Http.getString(ref.url);
        if (ref.sha256 != null && !ref.sha256.isEmpty()
            && !Hashing.sha256(text.getBytes(StandardCharsets.UTF_8)).equalsIgnoreCase(ref.sha256)) {
            throw new IOException("Checksum mismatch for " + ref.url);
        }
        return text;
    }

    public static void checkListed(final Release release) throws IOException {
        if (release == null || release.id == null || release.id.isBlank() || release.minecraft == null || release.minecraft.isBlank()) {
            throw new IOException("a release is missing its id or Minecraft version");
        }
        String prefix = BuildInfo.releaseDownloadsPrefix();
        for (Release.FileRef ref : List.of(release.manifest, release.delta, release.libraries)) {
            if (ref == null || ref.url == null || !ref.url.startsWith(prefix)) {
                throw new IOException("release " + release.id + " links outside " + prefix);
            }
        }
    }
}
