package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiMouseCursor;
import java.util.List;
import org.teamzetaverse.launcher.feed.Changelog;
import org.teamzetaverse.launcher.feed.Feed;

final class NewsPage {
    private final LauncherUi ui;

    NewsPage(final LauncherUi ui) {
        this.ui = ui;
    }

    private static final String[] TABS = {"News", "ABNW changelog", "Launcher changelog"};
    private int tab;

    void draw() {
        float full = ImGui.getContentRegionAvailX();
        float width = Math.min(full, px(880));
        float offset = (full - width) * 0.5f;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        ImGui.beginGroup();
        Widgets.pageHeader("News & updates", "What's new in A Brand New World, straight from the team.");
        this.tab = Widgets.segmented("news-tabs", TABS, this.tab, Math.min(width, px(520)));
        ImGui.dummy(0, px(10));
        switch (this.tab) {
            case 1 -> this.drawChangelog("abnw", this.ui.abnwChangelog, width, "ABNW");
            case 2 -> this.drawChangelog("launcher", this.ui.launcherChangelog, width, "Launcher");
            default -> this.drawNews(width);
        }
        ImGui.endGroup();
    }

    private void drawNews(final float width) {
        List<Feed.News> news = this.ui.feed.news;
        if (news.isEmpty()) {
            if (Widgets.beginCard("news-empty", width, 0, Theme.SURFACE, px(32), px(32))) {
                Widgets.cardTitle(Icons.Icon.PENGUIN_EMPTY, "Nothing here yet");
                Widgets.textWrapped(Fonts.body, Theme.MUTED, "Check back soon. Announcements, patch notes and sneak peeks will land here.");
            }
            Widgets.endCard();
        }
        for (int i = 0; i < news.size(); i++) {
            this.card("news-" + i, news.get(i), width, i == 0);
            ImGui.dummy(0, px(8));
        }
    }

    private void drawChangelog(final String id, final Changelog changelog, final float width, final String product) {
        if (changelog.entries.isEmpty()) {
            if (Widgets.beginCard(id + "-empty", width, 0, Theme.SURFACE, px(32), px(32))) {
                Widgets.cardTitle(Icons.Icon.SOON, "No changes listed yet");
                Widgets.textWrapped(Fonts.body, Theme.MUTED, "The " + product + " changelog will appear here with the next update.");
            }
            Widgets.endCard();
            return;
        }
        for (int i = 0; i < changelog.entries.size(); i++) {
            this.changelogCard(id + "-" + i, changelog.entries.get(i), width, i == 0);
            ImGui.dummy(0, px(8));
        }
    }

    private void changelogCard(final String id, final Changelog.Entry entry, final float width, final boolean latest) {
        float pad = px(26);
        float textWidth = width - pad * 2;
        float bulletIndent = px(20);
        float pillHeight = px(22);
        float titleHeight = entry.title.isEmpty() ? 0 : Widgets.textHeight(Fonts.heading, entry.title, textWidth);
        float[] changeHeights = new float[entry.changes.size()];
        float changesHeight = 0;
        for (int i = 0; i < changeHeights.length; i++) {
            changeHeights[i] = Widgets.textHeight(Fonts.body, entry.changes.get(i), textWidth - bulletIndent);
            changesHeight += changeHeights[i] + px(6);
        }
        float h = pad + pillHeight + (titleHeight > 0 ? px(12) + titleHeight : 0) + (changesHeight > 0 ? px(12) + changesHeight : 0) + pad;

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        if (latest) {
            Pixel.highlightCard(dl, x, y, x + width, y + h, u32(Theme.SURFACE));
        } else {
            Pixel.card(dl, x, y, x + width, y + h, 0f, u32(Theme.SURFACE));
        }
        float cx = x + pad;
        float cy = y + pad;
        String version = "v" + (entry.version.startsWith("v") ? entry.version.substring(1) : entry.version);
        Widgets.drawPill(dl, cx, cy, version, Theme.EMBER, Theme.EMBER, 0.14f, Icons.Icon.SPARK);
        float pillX = cx + Widgets.pillWidth(version, Icons.Icon.SPARK) + px(8);
        String date = Format.date(entry.date);
        if (!date.isEmpty()) {
            Widgets.drawPill(dl, pillX, cy, date, Theme.MUTED, 0xFFFFFF, 0.06f, Icons.Icon.CLOCK);
            pillX += Widgets.pillWidth(date, Icons.Icon.CLOCK) + px(8);
        }
        if (latest) {
            Widgets.drawPill(dl, pillX, cy, "Latest", Theme.SUN, Theme.SUN, 0.13f, null);
            float penguin = px(34);
            Icons.draw(dl, Icons.Icon.PENGUIN_HAPPY, pillX + Widgets.pillWidth("Latest", null) + px(4), cy + px(22) - penguin, penguin, u32(0xFFFFFF));
        }
        cy += pillHeight;
        if (titleHeight > 0) {
            cy += px(12);
            Widgets.drawTextWrapped(dl, Fonts.heading, cx, cy, u32(Theme.TEXT), entry.title, textWidth);
            cy += titleHeight;
        }
        if (changesHeight > 0) {
            cy += px(12);
            for (int i = 0; i < changeHeights.length; i++) {
                float dot = px(5);
                Pixel.dot(dl, cx + px(6), cy + Fonts.body.size() * 0.5f + px(1), dot * 0.5f, u32(Theme.EMBER));
                Widgets.drawTextWrapped(dl, Fonts.body, cx + bulletIndent, cy, u32(Theme.MUTED), entry.changes.get(i), textWidth - bulletIndent);
                cy += changeHeights[i] + px(6);
            }
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(width, h);
    }

    private void card(final String id, final Feed.News item, final float width, final boolean featured) {
        float pad = px(26);
        ImageCache.Texture image = item.image.isEmpty() ? null : this.ui.images.get(item.image, 1600);
        boolean hasImage = !item.image.isEmpty() && !this.ui.images.hasFailed(item.image);
        float imageHeight = hasImage ? width * (featured ? 0.42f : 0.32f) : 0;
        float textWidth = width - pad * 2;
        Fonts.Face titleFace = featured ? Fonts.title : Fonts.heading;
        float titleHeight = Widgets.textHeight(titleFace, item.title, textWidth);
        float summaryHeight = item.summary.isEmpty() ? 0 : Widgets.textHeight(Fonts.body, item.summary, textWidth);
        float pillHeight = px(22);
        float linkHeight = item.url.isEmpty() ? 0 : Fonts.labelSmall.size() + px(16);
        float h = imageHeight + pad + pillHeight + px(12) + titleHeight + (summaryHeight > 0 ? px(8) + summaryHeight : 0) + linkHeight + pad;

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean hovered = ImGui.isMouseHoveringRect(x, y, x + width, y + h) && ImGui.isWindowHovered();
        float hv = Motion.hover(id + "#hover", hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.card(dl, x, y, x + width, y + h, hv, u32(Theme.SURFACE));
        if (hasImage) {
            if (image != null) {
                drawTopCover(dl, image, x, y, width, imageHeight);
            } else {
                Pixel.rect(dl, x, y, x + width, y + imageHeight, u32(Theme.SURFACE_HI));
            }
        }
        float cy = y + imageHeight + pad;
        float cx = x + pad;
        float pillX = cx;
        if (!item.tag.isEmpty()) {
            Widgets.drawPill(dl, pillX, cy, item.tag.toUpperCase(), Theme.EMBER, Theme.EMBER, 0.14f, HomePage.iconFor(item.tag));
            pillX += Widgets.pillWidth(item.tag.toUpperCase(), Icons.Icon.CUBE) + px(8);
        }
        String date = Format.date(item.date);
        if (!date.isEmpty()) {
            Widgets.drawPill(dl, pillX, cy, date, Theme.MUTED, 0xFFFFFF, 0.06f, Icons.Icon.CLOCK);
        }
        cy += pillHeight + px(12);
        Widgets.drawTextWrapped(dl, titleFace, cx, cy, u32(Theme.TEXT), item.title, textWidth);
        cy += titleHeight;
        if (summaryHeight > 0) {
            cy += px(8);
            Widgets.drawTextWrapped(dl, Fonts.body, cx, cy, u32(Theme.MUTED), item.summary, textWidth);
            cy += summaryHeight;
        }
        if (!item.url.isEmpty()) {
            ImGui.setCursorScreenPos(cx, cy + px(14));
            if (Widgets.link(id + "-link", "Read more", Icons.Icon.EXTERNAL)) {
                Desktop.browse(item.url);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(width, h);
    }

    private static void drawTopCover(final ImDrawList dl, final ImageCache.Texture texture, final float x, final float y, final float w, final float h) {
        float box = w / h;
        float image = texture.aspect();
        float u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;
        if (image > box) {
            float span = box / image;
            u0 = (1f - span) * 0.5f;
            u1 = u0 + span;
        } else {
            float span = image / box;
            v0 = (1f - span) * 0.5f;
            v1 = v0 + span;
        }
        dl.addImage(texture.id(), x, y, x + w, y + h, u0, v0, u1, v1, u32(0xFFFFFF));
    }
}
