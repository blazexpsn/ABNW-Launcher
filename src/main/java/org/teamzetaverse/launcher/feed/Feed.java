package org.teamzetaverse.launcher.feed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class Feed {
    public int formatVersion = 1;
    public List<News> news = new ArrayList<>();
    public List<Announcement> announcements = new ArrayList<>();
    public List<Teaser> teasers = new ArrayList<>();
    public List<Screenshot> screenshots = new ArrayList<>();

    public static final class News {
        public String id = "";
        public String title = "";
        public String summary = "";
        public String date = "";
        public String tag = "";
        public String url = "";
        public String image = "";
    }

    public static final class Announcement {
        public String id = "";
        public String title = "";
        public String message = "";
        public String style = "toast";
        public String level = "info";
        public String startsAt = "";
        public String endsAt = "";
        public boolean once = true;
        public boolean sticky = false;
        public String actionLabel = "";
        public String actionUrl = "";
    }

    public static final class Teaser {
        public String text = "";
        public String hint = "";
    }

    public static final class Screenshot {
        public String url = "";
        public String caption = "";
        public String credit = "";
    }

    public static Feed empty() {
        return new Feed();
    }

    void sanitize() {
        this.news = this.news == null ? new ArrayList<>() : new ArrayList<>(this.news.stream().filter(n -> n != null && notBlank(n.title)).toList());
        this.announcements = this.announcements == null ? new ArrayList<>()
            : new ArrayList<>(this.announcements.stream().filter(a -> a != null && notBlank(a.id) && notBlank(a.title)).toList());
        this.teasers = this.teasers == null ? new ArrayList<>() : new ArrayList<>(this.teasers.stream().filter(t -> t != null && notBlank(t.text)).toList());
        this.screenshots = this.screenshots == null ? new ArrayList<>()
            : new ArrayList<>(this.screenshots.stream().filter(s -> s != null && isHttps(s.url)).toList());
        for (News item : this.news) {
            if (!isHttps(item.image)) {
                item.image = "";
            }
            if (!isHttps(item.url)) {
                item.url = "";
            }
        }
        for (Announcement item : this.announcements) {
            if (!isHttps(item.actionUrl)) {
                item.actionUrl = "";
            }
        }
    }

    static boolean notBlank(final String value) {
        return value != null && !value.isBlank();
    }

    public static boolean isHttps(final String value) {
        return value != null && value.startsWith("https://");
    }

    public static Instant parseTime(final String value) {
        if (!notBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (RuntimeException ignored) {
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
