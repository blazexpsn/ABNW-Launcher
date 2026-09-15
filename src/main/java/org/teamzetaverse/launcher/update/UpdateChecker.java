package org.teamzetaverse.launcher.update;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.release.ReleaseService;
import org.teamzetaverse.launcher.util.Json;

public final class UpdateChecker {
    public record LauncherUpdate(String version, String url) {
    }

    public record Status(Instant checkedAt, Release latestGame, String gameError, LauncherUpdate launcherUpdate, String launcherError) {
        public static Status pending() {
            return new Status(null, null, null, null, null);
        }

        public boolean checked() {
            return this.checkedAt != null;
        }
    }

    private final ReleaseService releases;

    public UpdateChecker(final ReleaseService releases) {
        this.releases = releases;
    }

    public Status check() {
        Release latest = null;
        String gameError = null;
        try {
            ReleaseService.Listing listing = this.releases.fetchListing();
            for (Release release : listing.releases()) {
                if (release.id.equals(listing.latest())) {
                    latest = release;
                }
            }
            if (latest == null && !listing.releases().isEmpty()) {
                latest = listing.releases().get(0);
            }
        } catch (IOException | RuntimeException e) {
            gameError = e.getMessage();
        }

        LauncherUpdate update = null;
        String launcherError = null;
        if (!BuildInfo.isDevBuild()) {
            try {
                JsonObject release = Http.getJson(BuildInfo.latestLauncherReleaseApi());
                String tag = Json.string(release, "tag_name");
                String url = Json.string(release, "html_url");
                if (tag != null && compare(tag, BuildInfo.VERSION) > 0) {
                    update = new LauncherUpdate(stripPrefix(tag), url != null && url.startsWith("https://github.com/") ? url : BuildInfo.launcherReleasesPage());
                }
            } catch (Http.StatusException e) {
                if (e.status != 404) {
                    launcherError = e.getMessage();
                }
            } catch (IOException | RuntimeException e) {
                launcherError = e.getMessage();
            }
        }
        return new Status(Instant.now(), latest, gameError, update, launcherError);
    }

    static String stripPrefix(final String version) {
        String trimmed = version.trim();
        return trimmed.startsWith("v") || trimmed.startsWith("V") ? trimmed.substring(1) : trimmed;
    }

    public static int compare(final String a, final String b) {
        String[] left = stripPrefix(a).split("[^0-9]+");
        String[] right = stripPrefix(b).split("[^0-9]+");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            long l = i < left.length ? parse(left[i]) : 0;
            long r = i < right.length ? parse(right[i]) : 0;
            if (l != r) {
                return Long.compare(l, r);
            }
        }
        return 0;
    }

    private static long parse(final String part) {
        if (part.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
