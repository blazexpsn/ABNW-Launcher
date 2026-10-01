package org.teamzetaverse.launcher.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class Format {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private Format() {
    }

    public static String date(final long epochMillis) {
        if (epochMillis <= 0) {
            return "Unknown";
        }
        return DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    public static String date(final String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        try {
            return DAY.format(Instant.parse(trimmed).atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return DAY.format(java.time.OffsetDateTime.parse(trimmed));
        } catch (DateTimeParseException ignored) {
        }
        if (trimmed.length() >= 10) {
            try {
                return DAY.format(LocalDate.parse(trimmed.substring(0, 10)));
            } catch (DateTimeParseException ignored) {
            }
        }
        return trimmed;
    }

    public static String duration(final long millis) {
        if (millis <= 0) {
            return "None yet";
        }
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours >= 24) {
            long days = hours / 24;
            long rest = hours % 24;
            return rest == 0 ? plural(days, "day") : plural(days, "day") + " " + plural(rest, "hour");
        }
        if (hours > 0) {
            return minutes == 0 ? plural(hours, "hour") : plural(hours, "hour") + " " + plural(minutes, "minute");
        }
        if (minutes > 0) {
            return plural(minutes, "minute");
        }
        return plural(Math.max(1, duration.toSeconds()), "second");
    }

    public static String clock(final long millis) {
        long total = Math.max(0, millis) / 1000L;
        long hours = total / 3600L;
        long minutes = total % 3600L / 60L;
        long seconds = total % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public static String relative(final long epochMillis) {
        if (epochMillis <= 0) {
            return "Never";
        }
        long elapsed = System.currentTimeMillis() - epochMillis;
        if (elapsed < 0) {
            return "Just now";
        }
        Duration duration = Duration.ofMillis(elapsed);
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return plural(minutes, "minute") + " ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return plural(hours, "hour") + " ago";
        }
        long days = duration.toDays();
        if (days < 7) {
            return plural(days, "day") + " ago";
        }
        if (days < 31) {
            return plural(days / 7, "week") + " ago";
        }
        if (days < 365) {
            return plural(days / 31, "month") + " ago";
        }
        return plural(days / 365, "year") + " ago";
    }

    private static String plural(final long count, final String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
