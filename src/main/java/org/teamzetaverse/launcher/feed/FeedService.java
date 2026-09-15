package org.teamzetaverse.launcher.feed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Json;

public final class FeedService {
    public static final String NEWS = "news.json";
    public static final String LAUNCHER_CHANGELOG = "launcher-changelog.json";
    public static final String ABNW_CHANGELOG = "abnw-changelog.json";
    private static final long MAX_DOCUMENT_BYTES = 4L * 1024 * 1024;

    public record Loaded(Feed news, Changelog launcherChangelog, Changelog abnwChangelog, boolean live) {
    }

    private interface Parser<T> {
        T parse(String text) throws IOException;
    }

    private record Document<T>(T value, boolean live) {
    }

    private final Path cacheDir;

    public FeedService(final LauncherPaths paths) {
        this.cacheDir = paths.cache().resolve("feed");
    }

    public Loaded load() {
        Document<Feed> news = this.fetch(NEWS, FeedService::parseFeed, Feed.empty());
        Document<Changelog> launcher = this.fetch(LAUNCHER_CHANGELOG, FeedService::parseChangelog, Changelog.empty());
        Document<Changelog> abnw = this.fetch(ABNW_CHANGELOG, FeedService::parseChangelog, Changelog.empty());
        return new Loaded(news.value(), launcher.value(), abnw.value(), news.live() && launcher.live() && abnw.live());
    }

    public static Loaded bundled() {
        return new Loaded(
            bundled(NEWS, FeedService::parseFeed, Feed.empty()),
            bundled(LAUNCHER_CHANGELOG, FeedService::parseChangelog, Changelog.empty()),
            bundled(ABNW_CHANGELOG, FeedService::parseChangelog, Changelog.empty()),
            false
        );
    }

    private <T> Document<T> fetch(final String name, final Parser<T> parser, final T empty) {
        Path cached = this.cacheDir.resolve(name);
        for (String url : new String[]{BuildInfo.feedApiUrl(name), BuildInfo.feedRawUrl(name)}) {
            try {
                String text = url.startsWith("https://api.github.com/")
                    ? new String(Http.getBytes(url, MAX_DOCUMENT_BYTES, Map.of("Accept", "application/vnd.github.raw+json", "X-GitHub-Api-Version", "2022-11-28")),
                        StandardCharsets.UTF_8)
                    : new String(Http.getBytes(url, MAX_DOCUMENT_BYTES), StandardCharsets.UTF_8);
                T value = parser.parse(text);
                try {
                    Json.writeString(cached, text);
                } catch (IOException e) {
                    System.err.println("Could not cache " + name + ": " + e.getMessage());
                }
                return new Document<>(value, true);
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not fetch " + name + " from " + url + ": " + e.getMessage());
            }
        }
        if (Files.isRegularFile(cached)) {
            try {
                return new Document<>(parser.parse(Files.readString(cached, StandardCharsets.UTF_8)), false);
            } catch (IOException | RuntimeException e) {
                System.err.println("Cached " + name + " is unreadable: " + e.getMessage());
            }
        }
        return new Document<>(bundled(name, parser, empty), false);
    }

    private static <T> T bundled(final String name, final Parser<T> parser, final T empty) {
        try (InputStream in = FeedService.class.getResourceAsStream("/feed/" + name)) {
            if (in != null) {
                return parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Bundled " + name + " is unreadable: " + e.getMessage());
        }
        return empty;
    }

    static Feed parseFeed(final String text) throws IOException {
        Feed feed;
        try {
            feed = Json.GSON.fromJson(text, Feed.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid news.json: " + e.getMessage(), e);
        }
        if (feed == null) {
            throw new IOException("empty news.json");
        }
        if (feed.formatVersion > 1) {
            throw new IOException("news.json uses a newer format");
        }
        feed.sanitize();
        return feed;
    }

    static Changelog parseChangelog(final String text) throws IOException {
        Changelog changelog;
        try {
            changelog = Json.GSON.fromJson(text, Changelog.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid changelog: " + e.getMessage(), e);
        }
        if (changelog == null) {
            throw new IOException("empty changelog");
        }
        if (changelog.formatVersion > 1) {
            throw new IOException("the changelog uses a newer format");
        }
        changelog.sanitize();
        return changelog;
    }
}
