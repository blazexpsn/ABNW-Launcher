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
        String left = stripBuild(stripPrefix(a));
        String right = stripBuild(stripPrefix(b));
        int dashLeft = left.indexOf('-');
        int dashRight = right.indexOf('-');
        String coreLeft = dashLeft < 0 ? left : left.substring(0, dashLeft);
        String coreRight = dashRight < 0 ? right : right.substring(0, dashRight);
        int byCore = compareCore(coreLeft, coreRight);
        if (byCore != 0) {
            return byCore;
        }
        String preLeft = dashLeft < 0 ? null : left.substring(dashLeft + 1);
        String preRight = dashRight < 0 ? null : right.substring(dashRight + 1);
        if (preLeft == null || preRight == null) {
            return preLeft == null ? (preRight == null ? 0 : 1) : -1;
        }
        return comparePrerelease(preLeft, preRight);
    }

    private static String stripBuild(final String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static int compareCore(final String left, final String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        int length = Math.max(l.length, r.length);
        for (int i = 0; i < length; i++) {
            long lv = i < l.length ? parseNumber(l[i]) : 0L;
            long rv = i < r.length ? parseNumber(r[i]) : 0L;
            if (lv != rv) {
                return Long.compare(lv, rv);
            }
        }
        return 0;
    }

    private static int comparePrerelease(final String left, final String right) {
        String[] l = left.split("[.-]");
        String[] r = right.split("[.-]");
        int length = Math.min(l.length, r.length);
        for (int i = 0; i < length; i++) {
            boolean ln = l[i].matches("\\d+");
            boolean rn = r[i].matches("\\d+");
            int result;
            if (ln && rn) {
                result = Long.compare(parseNumber(l[i]), parseNumber(r[i]));
            } else if (ln != rn) {
                result = ln ? -1 : 1;
            } else {
                result = l[i].compareToIgnoreCase(r[i]);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(l.length, r.length);
    }

    private static long parseNumber(final String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
