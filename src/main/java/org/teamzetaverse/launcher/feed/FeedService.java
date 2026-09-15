package org.teamzetaverse.launcher.feed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Json;

public final class FeedService {
    public record Loaded(Feed feed, boolean live) {
    }

    private final Path cacheFile;

    public FeedService(final LauncherPaths paths) {
        this.cacheFile = paths.cache().resolve("launcher-feed.json");
    }

    public Loaded load() {
        try {
            String text = Http.getString(BuildInfo.feedUrl());
            Feed feed = parse(text);
            try {
                Json.writeString(this.cacheFile, text);
            } catch (IOException e) {
                System.err.println("Could not cache the launcher feed: " + e.getMessage());
            }
            return new Loaded(feed, true);
        } catch (IOException | RuntimeException e) {
            System.err.println("Launcher feed unavailable, using the cached copy: " + e.getMessage());
        }
        if (Files.isRegularFile(this.cacheFile)) {
            try {
                return new Loaded(parse(Files.readString(this.cacheFile, StandardCharsets.UTF_8)), false);
            } catch (IOException | RuntimeException e) {
                System.err.println("Cached launcher feed is unreadable: " + e.getMessage());
            }
        }
        return new Loaded(bundled(), false);
    }

    public static Feed bundled() {
        try (InputStream in = FeedService.class.getResourceAsStream("/feed/launcher-feed.json")) {
            if (in != null) {
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Bundled launcher feed is unreadable: " + e.getMessage());
        }
        return Feed.empty();
    }

    static Feed parse(final String text) throws IOException {
        Feed feed;
        try {
            feed = Json.GSON.fromJson(text, Feed.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid launcher feed: " + e.getMessage(), e);
        }
        if (feed == null) {
            throw new IOException("empty launcher feed");
        }
        if (feed.formatVersion > 1) {
            throw new IOException("the launcher feed uses a newer format");
        }
        feed.sanitize();
        return feed;
    }
}
