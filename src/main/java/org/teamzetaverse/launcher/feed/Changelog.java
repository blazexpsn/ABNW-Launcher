package org.teamzetaverse.launcher.feed;

import java.util.ArrayList;
import java.util.List;

public final class Changelog {
    public int formatVersion = 1;
    public List<Entry> entries = new ArrayList<>();

    public static final class Entry {
        public String version = "";
        public String date = "";
        public String title = "";
        public List<String> changes = new ArrayList<>();
    }

    public static Changelog empty() {
        return new Changelog();
    }

    void sanitize() {
        List<Entry> clean = new ArrayList<>();
        if (this.entries != null) {
            for (Entry entry : this.entries) {
                if (entry == null || !Feed.notBlank(entry.version)) {
                    continue;
                }
                if (entry.title == null) {
                    entry.title = "";
                }
                if (entry.date == null) {
                    entry.date = "";
                }
                List<String> changes = new ArrayList<>();
                if (entry.changes != null) {
                    for (String change : entry.changes) {
                        if (Feed.notBlank(change)) {
                            changes.add(change.trim());
                        }
                    }
                }
                entry.changes = changes;
                clean.add(entry);
            }
        }
        this.entries = clean;
    }
}
