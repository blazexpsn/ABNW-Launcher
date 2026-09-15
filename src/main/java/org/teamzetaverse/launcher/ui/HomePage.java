package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import java.util.List;
import java.util.Optional;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.feed.Feed;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.launch.GameProcess;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.update.UpdateChecker;

final class HomePage {
    private static final double TEASER_SECONDS = 9.0;

    private final LauncherUi ui;

    HomePage(final LauncherUi ui) {
        this.ui = ui;
    }

    void draw() {
        float width = ImGui.getContentRegionAvailX();
        this.ui.announcements.drawBanners(width);
        this.drawHero(width);
        ImGui.dummy(0, px(18));

        float gap = px(20);
        float right = Math.max(px(300), width * 0.34f);
        float left = width - right - gap;
        ImGui.beginGroup();
        this.drawNews(left);
        ImGui.endGroup();
        ImGui.sameLine(0, gap);
        ImGui.beginGroup();
        this.drawStatus(right);
        ImGui.dummy(0, px(6));
        this.drawTeaser(right);
        ImGui.endGroup();
    }

    private void drawHero(final float width) {
        float height = Math.max(px(300), Math.min(px(470), width * 0.42f));
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float rounding = px(22);
        ImDrawList dl = ImGui.getWindowDrawList();
        this.ui.showcase.draw("hero", x, y, width, height, rounding);

        float fadeTop = y + height * 0.30f;
        float fadeBottom = y + height - rounding;
        dl.addRectFilledMultiColor(x, fadeTop, x + width, fadeBottom, u32(Theme.BG, 0f), u32(Theme.BG, 0f), u32(Theme.BG, 0.9f), u32(Theme.BG, 0.9f));
        dl.addRectFilled(x, fadeBottom - px(0.5f), x + width, y + height, u32(Theme.BG, 0.9f), rounding, ImDrawFlags.RoundCornersBottom);
        dl.addRectFilledMultiColor(x + rounding, y + height * 0.45f, x + width * 0.6f, fadeBottom, u32(Theme.BG, 0.35f), u32(Theme.BG, 0f),
            u32(Theme.BG, 0f), u32(Theme.BG, 0.35f));
        dl.addRect(x, y, x + width, y + height, u32(0xFFFFFF, 0.06f), rounding, 0, px(1));

        Showcase.Slide slide = this.ui.showcase.current();
        if (slide != null && slide.caption() != null && !slide.caption().isBlank()) {
            String caption = slide.credit() == null || slide.credit().isBlank() ? slide.caption() : slide.caption() + "  ·  " + slide.credit();
            Widgets.drawPill(dl, x + px(20), y + px(18), caption, Theme.TEXT, 0x0A0608, 0.6f, Icons.Icon.IMAGE);
        }

        Optional<Instance> selected = this.ui.selectedInstance();
        float pad = px(34);
        float buttonHeight = px(58);
        float playWidth = px(196);
        float pickerWidth = selected.isPresent() && this.ui.instances.all().size() > 0 ? px(230) : 0;
        float buttonsY = y + height - pad - buttonHeight;
        float playX = x + width - pad - playWidth;

        float textRight = (pickerWidth > 0 ? playX - px(12) - pickerWidth : playX) - px(24);
        float textWidth = textRight - (x + pad);
        String overline = selected.map(i -> i.name).orElse("A Brand New World");
        String headline;
        String status;
        int statusColor;
        if (selected.isEmpty()) {
            headline = "Your world awaits";
            status = "Create an instance to begin your adventure.";
            statusColor = Theme.SUN;
        } else {
            Instance instance = selected.get();
            headline = instance.release.displayName();
            GameProcess process = this.ui.running.get(instance.id);
            LauncherUi.InstallState state = this.ui.installState(instance);
            Release latest = this.ui.latestRelease();
            if (process != null) {
                status = "Playing now  ·  " + Format.clock(System.currentTimeMillis() - process.startedMillis());
                statusColor = Theme.OK;
            } else if (state == LauncherUi.InstallState.BUSY) {
                status = "Getting everything ready…";
                statusColor = Theme.SUN;
            } else if (this.ui.isOutdated(instance) && latest != null) {
                status = latest.displayName() + " is available";
                statusColor = Theme.SUN;
            } else if (state == LauncherUi.InstallState.NEEDS_INSTALL) {
                status = "Installs automatically on first launch";
                statusColor = Theme.MUTED;
            } else {
                status = instance.lastPlayed > 0 ? "Ready to play  ·  Last played " + Format.relative(instance.lastPlayed).toLowerCase() : "Ready to play";
                statusColor = Theme.OK;
            }
        }
        float headlineSize = Fonts.hero.size();
        float blockHeight = Fonts.overline.size() + px(6) + headlineSize + px(6) + Fonts.body.size();
        float ty = y + height - pad - blockHeight + px(2);
        float tx = x + pad;
        Widgets.drawOverline(dl, tx, ty, u32(Theme.SUN), Widgets.ellipsize(Fonts.overline, overline, textWidth / 1.2f));
        ty += Fonts.overline.size() + px(6);
        String fitted = Widgets.ellipsize(Fonts.hero, headline, textWidth);
        dl.addText(Fonts.hero.font(), (int)headlineSize, tx + px(1), ty + px(2), u32(0x000000, 0.35f), fitted);
        Widgets.drawText(dl, Fonts.hero, tx, ty, u32(Theme.TEXT), fitted);
        ty += headlineSize + px(6);
        float dot = px(8);
        dl.addCircleFilled(tx + dot * 0.5f, ty + Fonts.body.size() * 0.5f, dot, u32(statusColor, 0.2f));
        dl.addCircleFilled(tx + dot * 0.5f, ty + Fonts.body.size() * 0.5f, dot * 0.5f, u32(statusColor));
        Widgets.drawText(dl, Fonts.body, tx + dot + px(10), ty - px(1), u32(Theme.MUTED), Widgets.ellipsize(Fonts.body, status, textWidth - dot - px(10)));

        ImGui.setCursorScreenPos(playX, buttonsY);
        if (this.ui.currentAccount().isEmpty()) {
            if (Widgets.primary("hero-signin", "Sign in", Icons.Icon.USER, playWidth, buttonHeight, true)) {
                this.ui.startSignIn();
            }
        } else if (selected.isEmpty()) {
            if (Widgets.primary("hero-create", "Create", Icons.Icon.PLUS, playWidth, buttonHeight, true)) {
                this.ui.dialogs.openNewInstance();
            }
        } else {
            Instance instance = selected.get();
            if (this.ui.running.containsKey(instance.id)) {
                if (Widgets.button("hero-stop", "Stop", Icons.Icon.STOP, Widgets.Variant.SECONDARY, playWidth, buttonHeight, true)) {
                    this.ui.stop(instance);
                }
            } else {
                boolean busy = this.ui.installState(instance) == LauncherUi.InstallState.BUSY;
                if (Widgets.primary("hero-play", busy ? "Preparing" : "Play", Icons.Icon.PLAY, playWidth, buttonHeight, !busy)) {
                    this.ui.play(instance);
                }
            }
        }

        if (pickerWidth > 0 && selected.isPresent()) {
            float px0 = playX - px(12) - pickerWidth;
            ImGui.setCursorScreenPos(px0, buttonsY);
            boolean clicked = ImGui.invisibleButton("hero-picker", pickerWidth, buttonHeight);
            boolean hovered = ImGui.isItemHovered();
            if (hovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            float hv = Motion.hover("hero-picker#hover", hovered || ImGui.isPopupOpen("hero-instances"));
            dl.addRectFilled(px0, buttonsY, px0 + pickerWidth, buttonsY + buttonHeight, u32(0x0A0608, 0.55f + 0.15f * hv), px(14));
            dl.addRect(px0, buttonsY, px0 + pickerWidth, buttonsY + buttonHeight, u32(0xFFFFFF, 0.10f + 0.12f * hv), px(14), 0, px(1));
            float tile = px(34);
            float tileY = buttonsY + (buttonHeight - tile) * 0.5f;
            InstancesPage.drawTile(dl, selected.get(), px0 + px(12), tileY, tile);
            float labelX = px0 + px(12) + tile + px(11);
            float labelWidth = pickerWidth - (labelX - px0) - px(34);
            Widgets.drawText(dl, Fonts.tiny, labelX, buttonsY + px(11), u32(Theme.FAINT), "INSTANCE");
            Widgets.drawText(dl, Fonts.label, labelX, buttonsY + px(11) + Fonts.tiny.size() + px(3), u32(Theme.TEXT),
                Widgets.ellipsize(Fonts.label, selected.get().name, labelWidth));
            Icons.draw(dl, Icons.Icon.CHEVRON_DOWN, px0 + pickerWidth - px(28), buttonsY + (buttonHeight - px(14)) * 0.5f, px(14), u32(Theme.MUTED));
            if (clicked) {
                ImGui.openPopup("hero-instances");
            }
            ImGui.setNextWindowPos(px0, buttonsY - px(8), 0, 0f, 1f);
            ImGui.setNextWindowSize(pickerWidth + px(12) + playWidth, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(8), px(8));
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(4), px(4));
            if (ImGui.beginPopup("hero-instances")) {
                for (Instance instance : this.ui.instances.all()) {
                    boolean current = instance.id.equals(selected.get().id);
                    if (this.ui.menuItem("pick-" + instance.id, current ? Icons.Icon.CHECK : Icons.Icon.CUBE,
                        instance.name + "  ·  " + instance.release.displayName(), current)) {
                        this.ui.select(instance);
                        ImGui.closeCurrentPopup();
                    }
                }
                if (this.ui.menuItem("pick-new", Icons.Icon.PLUS, "New instance", false)) {
                    ImGui.closeCurrentPopup();
                    this.ui.dialogs.openNewInstance();
                }
                ImGui.endPopup();
            }
            ImGui.popStyleVar(2);
        }

        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(width, height);
    }

    private void drawNews(final float width) {
        Widgets.text(Fonts.heading, Theme.TEXT, "Latest news");
        ImGui.sameLine();
        Widgets.alignRight(Widgets.textWidth(Fonts.labelSmall, "All news") + px(19));
        ImGui.setCursorPosY(ImGui.getCursorPosY() + px(3));
        if (Widgets.link("home-all-news", "All news", Icons.Icon.CHEVRON_RIGHT)) {
            this.ui.navigate(LauncherUi.Page.NEWS);
        }
        ImGui.dummy(0, px(2));
        List<Feed.News> news = this.ui.feed.news;
        if (news.isEmpty()) {
            if (Widgets.beginCard("home-news-empty", width, 0)) {
                Widgets.textWrapped(Fonts.body, Theme.MUTED, "News from the ABNW team will show up here.");
            }
            Widgets.endCard();
            return;
        }
        for (int i = 0; i < Math.min(3, news.size()); i++) {
            Feed.News item = news.get(i);
            if (this.compactNews("home-news-" + i, item, width)) {
                if (!item.url.isEmpty()) {
                    Desktop.browse(item.url);
                } else {
                    this.ui.navigate(LauncherUi.Page.NEWS);
                }
            }
            ImGui.dummy(0, px(2));
        }
    }

    private boolean compactNews(final String id, final Feed.News item, final float width) {
        float pad = px(16);
        float thumbW = px(104);
        float thumbH = px(76);
        float h = thumbH + pad * 2;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, width, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover(id + "#hover", hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + width, y + h, u32(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv)), px(16));
        dl.addRect(x, y, x + width, y + h, u32(Theme.mix(Theme.BORDER_SOFT, Theme.EMBER_LO, hv * 0.6f)), px(16), 0, px(1));
        float tx = x + pad;
        float ty = y + pad;
        ImageCache.Texture image = item.image.isEmpty() ? null : this.ui.images.get(item.image, 512);
        if (image != null) {
            Showcase.drawCover(dl, image, tx, ty, thumbW, thumbH, px(11), 1f, 0.5f);
        } else {
            dl.addRectFilledMultiColor(tx, ty, tx + thumbW, ty + thumbH, u32(Theme.EMBER_DEEP), u32(Theme.MAROON), u32(Theme.EMBER_DEEP), u32(0x1A0B08));
            dl.addRect(tx, ty, tx + thumbW, ty + thumbH, u32(Theme.SURFACE), px(11), 0, px(3));
            float iconSize = px(28);
            Icons.draw(dl, iconFor(item.tag), tx + (thumbW - iconSize) * 0.5f, ty + (thumbH - iconSize) * 0.5f, iconSize, u32(Theme.EMBER, 0.85f));
        }
        float bx = tx + thumbW + px(16);
        float bw = x + width - pad - bx;
        String meta = (item.tag.isEmpty() ? "" : item.tag.toUpperCase()) + (item.tag.isEmpty() || item.date.isEmpty() ? "" : "  ·  ")
            + Format.date(item.date);
        Widgets.drawText(dl, Fonts.tiny, bx, ty, u32(Theme.EMBER), Widgets.ellipsize(Fonts.tiny, meta, bw));
        float titleY = ty + Fonts.tiny.size() + px(5);
        Widgets.drawText(dl, Fonts.label, bx, titleY, u32(Theme.TEXT), Widgets.ellipsize(Fonts.label, item.title, bw));
        float summaryY = titleY + Fonts.label.size() + px(5);
        List<String> lines = Widgets.clampLines(Fonts.small, item.summary, bw, 2);
        for (String line : lines) {
            Widgets.drawText(dl, Fonts.small, bx, summaryY, u32(Theme.MUTED), line);
            summaryY += Fonts.small.size() + px(3);
        }
        return clicked;
    }

    static Icons.Icon iconFor(final String tag) {
        String lower = tag == null ? "" : tag.toLowerCase();
        if (lower.contains("launcher")) {
            return Icons.Icon.HOME;
        }
        if (lower.contains("engine") || lower.contains("render")) {
            return Icons.Icon.CHIP;
        }
        if (lower.contains("release") || lower.contains("update")) {
            return Icons.Icon.SPARK;
        }
        return Icons.Icon.CUBE;
    }

    private void drawStatus(final float width) {
        if (Widgets.beginCard("home-status", width, 0)) {
            Widgets.cardTitle(Icons.Icon.CUBE, "Status");
            UpdateChecker.Status status = this.ui.updateStatus;
            Release latest = status.latestGame();

            String gameValue = latest != null ? latest.displayName() : status.checked() ? "Unavailable" : "Checking…";
            this.statusRow("Latest build", gameValue, latest != null ? Theme.OK : status.checked() ? Theme.WARN : Theme.FAINT);

            UpdateChecker.LauncherUpdate update = status.launcherUpdate();
            String launcherValue = BuildInfo.isDevBuild() ? "Development" : "v" + BuildInfo.VERSION;
            this.statusRow("Launcher", launcherValue, update != null ? Theme.SUN : Theme.OK);

            String discordValue = switch (this.ui.discord.state()) {
                case CONNECTED -> "Connected";
                case CONNECTING -> "Connecting…";
                case DISCORD_NOT_RUNNING -> "Not running";
                case DISABLED -> "Off";
                case NOT_CONFIGURED -> "Unavailable";
            };
            int discordColor = this.ui.discord.state() == org.teamzetaverse.launcher.discord.DiscordPresence.State.CONNECTED ? Theme.OK : Theme.FAINT;
            this.statusRow("Discord", discordValue, discordColor);

            Widgets.divider();
            String checked = this.ui.checkingUpdates ? "Checking for updates…"
                : status.checked() ? "Checked " + Format.relative(status.checkedAt().toEpochMilli()).toLowerCase() : "Checking for updates…";
            Widgets.text(Fonts.small, Theme.FAINT, checked);
            ImGui.sameLine();
            Widgets.alignRight(px(28));
            ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
            if (Widgets.iconButton("home-check-updates", Icons.Icon.REFRESH, px(28), "Check now", !this.ui.checkingUpdates)) {
                this.ui.checkForUpdatesNow();
            }
            if (update != null) {
                ImGui.dummy(0, px(4));
                if (Widgets.primary("home-launcher-update", "Get launcher v" + update.version(), Icons.Icon.DOWNLOAD, -1, px(40), true)) {
                    Desktop.browse(update.url());
                }
            }
        }
        Widgets.endCard();
    }

    private void statusRow(final String label, final String value, final int color) {
        Widgets.text(Fonts.body, Theme.MUTED, label);
        ImGui.sameLine();
        float valueWidth = Widgets.textWidth(Fonts.label, value) + px(18);
        Widgets.alignRight(valueWidth);
        Widgets.statusDot(color);
        ImGui.sameLine(0, px(10));
        Widgets.text(Fonts.label, Theme.TEXT, value);
    }

    private void drawTeaser(final float width) {
        List<Feed.Teaser> teasers = this.ui.feed.teasers;
        if (teasers.isEmpty()) {
            return;
        }
        double now = ImGui.getTime();
        int index = (int)((now / TEASER_SECONDS) % teasers.size());
        double phase = (now % TEASER_SECONDS) / TEASER_SECONDS;
        float fadeIn = (float)Math.min(1.0, phase * TEASER_SECONDS / 0.8);
        float fadeOut = (float)Math.min(1.0, (1.0 - phase) * TEASER_SECONDS / 0.8);
        float alpha = Math.min(fadeIn, fadeOut);
        if (alpha < 1f) {
            Motion.keepAlive();
        }
        Feed.Teaser teaser = teasers.get(index);

        float pad = px(18);
        float textWidth = width - pad * 2 - px(34);
        float textHeight = Widgets.textHeight(Fonts.label, teaser.text, textWidth);
        float h = pad * 2 + Fonts.tiny.size() + px(6) + Math.max(textHeight, Fonts.label.size() * 2 + px(4));
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + width, y + h, u32(Theme.BG), px(16));
        dl.addRect(x, y, x + width, y + h, u32(Theme.BORDER_SOFT), px(16), 0, px(1));
        float pulse = 0.55f + 0.2f * (float)Math.sin(now * 1.6);
        Icons.draw(dl, Icons.Icon.SPARK, x + pad, y + pad, px(20), u32(Theme.SUN, pulse));
        float tx = x + pad + px(34);
        String hint = teaser.hint == null || teaser.hint.isBlank() ? "Coming soon" : teaser.hint;
        Widgets.drawOverline(dl, tx, y + pad, u32(Theme.FAINT, alpha), hint);
        Widgets.drawTextWrapped(dl, Fonts.label, tx, y + pad + Fonts.tiny.size() + px(6), u32(Theme.MUTED, alpha), teaser.text, textWidth);
        if (teasers.size() > 1) {
            float dot = px(4);
            float dx = x + width - pad - (teasers.size() * (dot + px(4)) - px(4));
            for (int i = 0; i < teasers.size(); i++) {
                dl.addCircleFilled(dx + dot * 0.5f, y + pad + dot * 0.5f, dot * 0.5f, u32(i == index ? Theme.SUN : Theme.FAINT, i == index ? 0.8f : 0.4f));
                dx += dot + px(4);
            }
        }
        ImGui.dummy(width, h);
    }
}
