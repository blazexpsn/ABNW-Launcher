package org.teamzetaverse.launcher.release;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.FileMoves;
import org.teamzetaverse.launcher.util.Hashing;
import org.teamzetaverse.launcher.util.Json;

/**
 * Imports an unreleased ABNW build from a local .xdelta, for testing builds before they are published.
 *
 * <p>The build is the pair {@code deltaPatchedMinecraft} writes into {@code build/dist}:
 * {@code abnw-<version>-mc<minecraft>.xdelta} and its manifest, {@code abnw-<version>-mc<minecraft>.json}, which must sit
 * next to each other. The libraries come from {@code <same name>.libraries.json} or
 * {@code org.teamzetaverse.abnw.libraries.json} next to them if either exists, otherwise from the latest official
 * release.
 *
 * <p>Unlike an official release, nothing vouches for an imported patch: it replaces Minecraft's own code, so it can do
 * anything the player's account can. {@link #inspect} only reads and checks the files; the caller has to ask the player
 * whether they trust the patch before {@link #install} copies it in.
 */
public final class TestBuildImport {
    /** The only Minecraft version a test build may patch: the one ABNW is built on. */
    public static final String MINECRAFT_VERSION = "26.1.2";

    private static final String DELTA_EXTENSION = ".xdelta";
    private static final String SHARED_LIBRARIES_FILE = "org.teamzetaverse.abnw.libraries.json";
    private static final long MAX_DELTA_BYTES = 512L * 1024 * 1024;
    private static final long MAX_METADATA_BYTES = 8L * 1024 * 1024;

    private TestBuildImport() {
    }

    /**
     * An inspected build, ready to show to the player.
     *
     * @param librariesSource where the libraries came from, in words, for the confirmation dialog
     */
    public record Candidate(Path deltaFile, Release release, String librariesJson, String librariesSource) {
    }

    public static boolean isTestBuild(final Path file) {
        return file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(DELTA_EXTENSION);
    }

    public static Candidate inspect(final Path deltaFile, final ReleaseService releases, final Progress progress)
        throws IOException, Progress.CancelledException {
        String fileName = deltaFile.getFileName().toString();
        if (!isTestBuild(deltaFile) || !Files.isRegularFile(deltaFile)) {
            throw new IOException(fileName + " is not an ABNW .xdelta patch.");
        }
        String base = fileName.substring(0, fileName.length() - DELTA_EXTENSION.length());
        Path folder = deltaFile.toAbsolutePath().getParent();

        progress.status("Reading " + base + ".json…");
        Path manifestFile = folder.resolve(base + ".json");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException(fileName + " needs its manifest next to it, named " + base + ".json. "
                + "deltaPatchedMinecraft writes the two together into build/dist.");
        }
        JsonObject manifest = Json.parseObject(readSmall(manifestFile));

        String version = Json.string(manifest, "version");
        if (version == null || !version.matches("[A-Za-z0-9._+-]{1,64}")) {
            throw new IOException(base + ".json does not name a usable ABNW version.");
        }
        String minecraft = Json.string(manifest, "minecraftVersion");
        if (!MINECRAFT_VERSION.equals(minecraft)) {
            throw new IOException(fileName + " is built for Minecraft " + minecraft + ". Only builds for Minecraft "
                + MINECRAFT_VERSION + " can be imported.");
        }
        String format = Json.string(manifest, "format");
        if (format != null && !format.equals("vcdiff")) {
            throw new IOException(fileName + " uses a patch format this launcher does not support.");
        }

        Release release = new Release();
        release.id = version;
        release.name = "ABNW " + version + " (test build)";
        release.minecraft = minecraft;
        release.imported = true;
        release.delta = ReleaseService.fileRef(Json.object(manifest, "delta"));
        release.source = ReleaseService.fileRef(Json.object(manifest, "source"));
        release.target = ReleaseService.fileRef(Json.object(manifest, "target"));
        if (!release.isResolved()) {
            throw new IOException(base + ".json is missing the patch's hashes.");
        }

        progress.checkCancelled();
        progress.status("Checking " + fileName + "…");
        long size = Files.size(deltaFile);
        if (size > MAX_DELTA_BYTES) {
            throw new IOException(fileName + " is larger than " + MAX_DELTA_BYTES / (1024 * 1024) + " MB.");
        }
        if (!Hashing.matches(deltaFile, release.delta.sha256, release.delta.size)) {
            throw new IOException(fileName + " does not match its manifest. Copy both files from the same build.");
        }

        progress.checkCancelled();
        String librariesJson;
        String librariesSource;
        Path libraries = folder.resolve(base + ".libraries.json");
        if (!Files.isRegularFile(libraries)) {
            libraries = folder.resolve(SHARED_LIBRARIES_FILE);
        }
        if (Files.isRegularFile(libraries)) {
            librariesJson = readSmall(libraries);
            librariesSource = libraries.getFileName().toString();
        } else {
            progress.status("Fetching the libraries of the latest ABNW release…");
            ReleaseService.Listing listing = releases.fetchListing();
            Release latest = listing.releases().stream()
                .filter(candidate -> candidate.id.equals(listing.latest()))
                .findFirst()
                .orElse(listing.releases().isEmpty() ? null : listing.releases().get(0));
            if (latest == null) {
                throw new IOException(fileName + " has no libraries file next to it (" + SHARED_LIBRARIES_FILE
                    + "), and there is no official release to borrow them from.");
            }
            librariesJson = releases.resolve(latest).librariesJson();
            librariesSource = latest.displayName() + ", the latest release";
        }
        if (!"org.teamzetaverse.abnw.libraries".equals(Json.string(Json.parseObject(librariesJson), "uid"))) {
            throw new IOException(librariesSource + " is not an ABNW libraries component.");
        }

        return new Candidate(deltaFile, release, librariesJson, librariesSource);
    }

    /** Copies the confirmed patch into the delta cache, where the installer finds it instead of downloading. */
    public static void install(final Candidate candidate, final Path deltasDir) throws IOException {
        Release release = candidate.release();
        Files.createDirectories(deltasDir);
        Path target = deltasDir.resolve(release.deltaFileName());
        Path temp = deltasDir.resolve(release.deltaFileName() + ".part");
        Files.copy(candidate.deltaFile(), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        // The file could have changed between inspecting it and the player confirming.
        if (!Hashing.matches(temp, release.delta.sha256, release.delta.size)) {
            Files.deleteIfExists(temp);
            throw new IOException(candidate.deltaFile().getFileName() + " changed after it was checked. Import it again.");
        }
        FileMoves.replace(temp, target);
    }

    private static String readSmall(final Path file) throws IOException {
        if (Files.size(file) > MAX_METADATA_BYTES) {
            throw new IOException(file.getFileName() + " is larger than " + MAX_METADATA_BYTES / (1024 * 1024) + " MB.");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
