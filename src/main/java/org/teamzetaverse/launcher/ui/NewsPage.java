package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiMouseCursor;
import java.util.List;
import org.teamzetaverse.launcher.feed.Feed;

final class NewsPage {
    private final LauncherUi ui;

    NewsPage(final LauncherUi ui) {
        this.ui = ui;
    }

    void draw() {
        float full = ImGui.getContentRegionAvailX();
        float width = Math.min(full, px(880));
        float offset = (full - width) * 0.5f;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        ImGui.beginGroup();
        Widgets.pageHeader("News & updates", "What's new in A Brand New World, straight from the team.");
        List<Feed.News> news = this.ui.feed.news;
        if (news.isEmpty()) {
            if (Widgets.beginCard("news-empty", width, 0, Theme.SURFACE, px(32), px(32))) {
                Widgets.cardTitle(Icons.Icon.NEWS, "Nothing here yet");
                Widgets.textWrapped(Fonts.body, Theme.MUTED, "Check back soon. Announcements, patch notes and sneak peeks will land here.");
            }
            Widgets.endCard();
        }
        for (int i = 0; i < news.size(); i++) {
            this.card("news-" + i, news.get(i), width, i == 0);
            ImGui.dummy(0, px(8));
        }
        ImGui.endGroup();
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
        dl.addRectFilled(x, y, x + width, y + h, u32(Theme.SURFACE), px(20));
        dl.addRect(x, y, x + width, y + h, u32(Theme.mix(Theme.BORDER_SOFT, Theme.EMBER_LO, hv * 0.5f)), px(20), 0, px(1));
        if (hasImage) {
            if (image != null) {
                drawTopCover(dl, image, x, y, width, imageHeight);
            } else {
                dl.addRectFilled(x, y, x + width, y + imageHeight, u32(Theme.SURFACE_HI), px(20), ImDrawFlags.RoundCornersTop);
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
        dl.addImageRounded(texture.id(), x, y, x + w, y + h, u0, v0, u1, v1, u32(0xFFFFFF), px(20), ImDrawFlags.RoundCornersTop);
    }
}
