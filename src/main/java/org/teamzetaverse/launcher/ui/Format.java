package org.teamzetaverse.launcher.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.teamzetaverse.launcher.feed.Feed;

final class Format {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private Format() {
    }

    static String date(final long millis) {
        return millis <= 0 ? "—" : DATE.format(Instant.ofEpochMilli(millis));
    }

    static String date(final String isoDate) {
        Instant instant = Feed.parseTime(isoDate);
        return instant == null ? "" : DATE.format(instant);
    }

    static String relative(final long millis) {
        if (millis <= 0) {
            return "Never";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 60) {
            return "Just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        }
        long days = hours / 24;
        if (days < 30) {
            return days == 1 ? "Yesterday" : days + " days ago";
        }
        return date(millis);
    }

    static String duration(final long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + " h " + minutes + " min";
        }
        return minutes + " min";
    }

    static String clock(final long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long hours = duration.toHours();
        return hours > 0
            ? String.format("%d:%02d:%02d", hours, duration.toMinutesPart(), duration.toSecondsPart())
            : String.format("%d:%02d", duration.toMinutesPart(), duration.toSecondsPart());
    }
}
