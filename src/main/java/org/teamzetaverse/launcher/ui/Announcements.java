package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teamzetaverse.launcher.LauncherConfig;
import org.teamzetaverse.launcher.feed.Feed;

final class Announcements {
    static final class Entry {
        String id = "";
        String title = "";
        String message = "";
        String style = "toast";
        String level = "info";
        boolean once = true;
        boolean sticky;
        String actionLabel = "";
        Runnable action;
        Runnable onDismiss;
        Instant startsAt;
        Instant endsAt;
        double firstShown = -1;

        boolean isBanner() {
            return "banner".equalsIgnoreCase(this.style);
        }

        int color() {
            return switch (this.level == null ? "" : this.level.toLowerCase()) {
                case "success" -> Theme.OK;
                case "warning" -> Theme.WARN;
                case "launch", "release" -> Theme.SUN;
                default -> Theme.EMBER;
            };
        }

        Icons.Icon icon() {
            return switch (this.level == null ? "" : this.level.toLowerCase()) {
                case "success" -> Icons.Icon.CHECK;
                case "warning" -> Icons.Icon.ALERT;
                case "launch", "release" -> Icons.Icon.SPARK;
                default -> Icons.Icon.MEGAPHONE;
            };
        }
    }

    private static final double TOAST_SECONDS = 10.0;

    private final LauncherConfig config;
    private final List<Entry> fromFeed = new ArrayList<>();
    private final Map<String, Entry> generated = new LinkedHashMap<>();
    private final Set<String> sessionDismissed = new HashSet<>();

    Announcements(final LauncherConfig config) {
        this.config = config;
    }

    void setFeed(final List<Feed.Announcement> items, final java.util.function.Consumer<String> openUrl) {
        this.fromFeed.clear();
        for (Feed.Announcement item : items) {
            Entry entry = new Entry();
            entry.id = "feed:" + item.id;
            entry.title = item.title;
            entry.message = item.message == null ? "" : item.message;
            entry.style = item.style;
            entry.level = item.level;
            entry.once = item.once;
            entry.sticky = item.sticky;
            entry.startsAt = Feed.parseTime(item.startsAt);
            entry.endsAt = Feed.parseTime(item.endsAt);
            if (item.actionLabel != null && !item.actionLabel.isBlank() && Feed.isHttps(item.actionUrl)) {
                entry.actionLabel = item.actionLabel;
                String url = item.actionUrl;
                entry.action = () -> openUrl.accept(url);
            }
            this.fromFeed.add(entry);
        }
    }

    void put(final Entry entry) {
        this.generated.put(entry.id, entry);
    }

    void remove(final String id) {
        this.generated.remove(id);
    }

    private List<Entry> active() {
        Instant now = Instant.now();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : this.all()) {
            if (this.sessionDismissed.contains(entry.id) || (entry.once && this.config.dismissedAnnouncements.contains(entry.id))) {
                continue;
            }
            if (entry.startsAt != null && now.isBefore(entry.startsAt)) {
                continue;
            }
            if (entry.endsAt != null && now.isAfter(entry.endsAt)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private List<Entry> all() {
        List<Entry> all = new ArrayList<>(this.generated.values());
        all.addAll(this.fromFeed);
        return all;
    }

    void dismiss(final Entry entry, final boolean remember) {
        this.sessionDismissed.add(entry.id);
        if (remember && entry.once && !this.config.dismissedAnnouncements.contains(entry.id)) {
            this.config.dismissedAnnouncements.add(entry.id);
            if (this.config.dismissedAnnouncements.size() > 200) {
                this.config.dismissedAnnouncements.remove(0);
            }
            this.config.save();
        }
        if (entry.onDismiss != null) {
            entry.onDismiss.run();
        }
    }

    boolean hasToasts() {
        return this.active().stream().anyMatch(e -> !e.isBanner());
    }

    void drawBanners(final float width) {
        for (Entry entry : this.active()) {
            if (!entry.isBanner()) {
                continue;
            }
            ImGui.pushID(entry.id);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            float pad = px(18);
            float iconBox = px(38);
            float closeSize = px(30);
            float actionWidth = entry.action != null ? Widgets.textWidth(Fonts.label, entry.actionLabel) + px(36) : 0;
            float textWidth = width - pad * 2 - iconBox - px(14) - closeSize - px(8) - (actionWidth > 0 ? actionWidth + px(12) : 0);
            float messageHeight = entry.message.isEmpty() ? 0 : Widgets.textHeight(Fonts.small, entry.message, textWidth);
            float h = Math.max(iconBox, Fonts.label.size() + (messageHeight > 0 ? px(4) + messageHeight : 0)) + pad * 1.4f;
            int accent = entry.color();
            ImDrawList dl = ImGui.getWindowDrawList();
            dl.addRectFilled(x, y, x + width, y + h, u32(Theme.SURFACE), px(16));
            dl.addRectFilledMultiColor(x + px(16), y, x + width * 0.55f, y + h, u32(accent, 0.10f), u32(accent, 0f), u32(accent, 0f), u32(accent, 0.10f));
            dl.addRect(x, y, x + width, y + h, u32(accent, 0.35f), px(16), 0, px(1));
            dl.addRectFilled(x, y + px(14), x + px(3), y + h - px(14), u32(accent), px(2));
            float ix = x + pad;
            float iy = y + (h - iconBox) * 0.5f;
            dl.addRectFilled(ix, iy, ix + iconBox, iy + iconBox, u32(accent, 0.16f), px(11));
            Icons.draw(dl, entry.icon(), ix + px(9), iy + px(9), iconBox - px(18), u32(accent));
            float tx = ix + iconBox + px(14);
            float contentHeight = Fonts.label.size() + (messageHeight > 0 ? px(4) + messageHeight : 0);
            float ty = y + (h - contentHeight) * 0.5f;
            Widgets.drawText(dl, Fonts.label, tx, ty, u32(Theme.TEXT), entry.title);
            if (messageHeight > 0) {
                Widgets.drawTextWrapped(dl, Fonts.small, tx, ty + Fonts.label.size() + px(4), u32(Theme.MUTED), entry.message, textWidth);
            }
            float right = x + width - pad;
            ImGui.setCursorScreenPos(right - closeSize, y + (h - closeSize) * 0.5f);
            if (Widgets.iconButton("close", Icons.Icon.CLOSE, closeSize, "Dismiss", true)) {
                this.dismiss(entry, true);
            }
            if (entry.action != null) {
                ImGui.setCursorScreenPos(right - closeSize - px(8) - actionWidth, y + (h - px(36)) * 0.5f);
                if (Widgets.button("action", entry.actionLabel, null, Widgets.Variant.SECONDARY, actionWidth, px(36), true)) {
                    entry.action.run();
                    this.dismiss(entry, true);
                }
            }
            ImGui.setCursorScreenPos(x, y);
            ImGui.dummy(width, h);
            ImGui.dummy(0, px(6));
            ImGui.popID();
        }
    }

    void drawToasts(final float bottomOffset) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        double now = ImGui.getTime();
        float width = px(360);
        float right = viewport.getWorkPosX() + viewport.getWorkSizeX() - px(24);
        float bottom = viewport.getWorkPosY() + viewport.getWorkSizeY() - px(24) - bottomOffset;
        int shown = 0;
        for (Entry entry : this.active()) {
            if (entry.isBanner() || shown >= 3) {
                continue;
            }
            if (entry.firstShown < 0) {
                entry.firstShown = now;
            }
            if (!entry.sticky && now - entry.firstShown > TOAST_SECONDS) {
                this.dismiss(entry, false);
                continue;
            }
            float enter = Motion.easeOutCubic((float)Math.min(1.0, (now - entry.firstShown) / 0.45));
            if (enter < 1f) {
                Motion.keepAlive();
            }
            float slide = (1f - enter) * px(40);
            float[] bg = Theme.rgba(Theme.SURFACE_HI, 1f);
            float[] border = Theme.rgba(entry.color(), 0.45f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(16), px(14));
            ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, px(16));
            ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, px(1));
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, Math.max(0.01f, enter));
            ImGui.pushStyleColor(ImGuiCol.WindowBg, bg[0], bg[1], bg[2], 0.98f);
            ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], border[3]);
            ImGui.setNextWindowPos(right + slide, bottom, 0, 1f, 1f);
            ImGui.setNextWindowSize(width, 0);
            int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.AlwaysAutoResize;
            if (ImGui.begin("##toast-" + entry.id, flags)) {
                float x = ImGui.getCursorScreenPosX();
                float y = ImGui.getCursorScreenPosY();
                float iconBox = px(34);
                ImDrawList dl = ImGui.getWindowDrawList();
                dl.addRectFilled(x, y, x + iconBox, y + iconBox, u32(entry.color(), 0.16f), px(10));
                Icons.draw(dl, entry.icon(), x + px(8), y + px(8), iconBox - px(16), u32(entry.color()));
                ImGui.setCursorScreenPos(x + iconBox + px(12), y - px(1));
                ImGui.beginGroup();
                float textWidth = width - px(32) - iconBox - px(12) - px(28);
                Widgets.text(Fonts.label, Theme.TEXT, Widgets.ellipsize(Fonts.label, entry.title, textWidth));
                if (!entry.message.isEmpty()) {
                    ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
                    ImGui.pushTextWrapPos(ImGui.getCursorPosX() + textWidth);
                    Fonts.small.push();
                    Theme.pushText(Theme.MUTED);
                    ImGui.textUnformatted(entry.message);
                    ImGui.popStyleColor();
                    ImGui.popFont();
                    ImGui.popTextWrapPos();
                }
                if (entry.action != null) {
                    ImGui.dummy(0, px(2));
                    if (Widgets.button("toast-action", entry.actionLabel, null, Widgets.Variant.PRIMARY, 0, px(34), true)) {
                        entry.action.run();
                        this.dismiss(entry, true);
                    }
                }
                ImGui.endGroup();
                float closeSize = px(26);
                ImGui.setCursorScreenPos(x + width - px(32) - closeSize, y - px(4));
                if (Widgets.iconButton("toast-close", Icons.Icon.CLOSE, closeSize, null, true)) {
                    this.dismiss(entry, true);
                }
                if (!entry.sticky && ImGui.isWindowHovered()) {
                    entry.firstShown = Math.max(entry.firstShown, now - TOAST_SECONDS + 3.0);
                }
                bottom = ImGui.getWindowPosY() - px(10);
            }
            ImGui.end();
            ImGui.popStyleColor(2);
            ImGui.popStyleVar(4);
            shown++;
        }
    }
}
