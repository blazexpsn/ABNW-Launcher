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
        ImGui.dummy(0, px(26));

        Widgets.pixelHeading("Latest news");
        ImGui.sameLine();
        Widgets.alignRight(Widgets.textWidth(Fonts.labelSmall, "All news") + px(19));
        if (Widgets.link("home-all-news", "All news", Icons.Icon.CHEVRON_RIGHT)) {
            this.ui.navigate(LauncherUi.Page.NEWS);
        }
        ImGui.dummy(0, px(4));
        this.drawNews(width);
        ImGui.dummy(0, px(22));

        float gap = px(20);
        float half = (width - gap) * 0.5f;
        ImGui.beginGroup();
        this.drawStatus(half);
        ImGui.endGroup();
        ImGui.sameLine(0, gap);
        ImGui.beginGroup();
        this.drawTeaser(half);
        ImGui.endGroup();
    }
    private void drawHero(final float width) {
        float u = Pixel.unit();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float horizon = this.ui.world.horizonY();
        float height = Math.max(px(260), horizon - y);
        ImDrawList dl = ImGui.getWindowDrawList();

        Optional<Instance> selected = this.ui.selectedInstance();
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

        float buttonHeight = u * 29f;
        float playWidth = px(210);
        boolean hasPicker = selected.isPresent() && !this.ui.instances.all().isEmpty();
        float pickerWidth = hasPicker ? px(260) : 0;
        float buttonsY = horizon - buttonHeight + u;
        float rowX = x;
        float playX = rowX + (hasPicker ? pickerWidth + u * 6 : 0);

        float textWidth = Math.min(width * 0.62f, Math.max(px(420), playX + playWidth - x));
        float headlineScale = PixelText.headline();
        java.util.List<String> lines = wrap(headline.toUpperCase(), headlineScale, textWidth);
        float blockHeight = PixelText.height(PixelText.small()) + u * 6 + lines.size() * (PixelText.height(headlineScale) + u * 5) + Fonts.body.size();
        float ty = buttonsY - u * 10 - blockHeight;
        PixelText.draw(dl, x, ty, PixelText.fit(overline.toUpperCase(), PixelText.small(), textWidth), PixelText.small(), u32(Theme.SUN));
        ty += PixelText.height(PixelText.small()) + u * 6;
        for (String line : lines) {
            PixelText.draw(dl, x, ty, line, headlineScale, u32(Theme.TEXT));
            ty += PixelText.height(headlineScale) + u * 5;
        }
        float dot = px(8);
        Pixel.dot(dl, x + dot * 0.5f, ty + Fonts.body.size() * 0.5f, dot, u32(statusColor, 0.25f));
        Pixel.dot(dl, x + dot * 0.5f, ty + Fonts.body.size() * 0.5f, dot * 0.5f, u32(statusColor));
        Widgets.drawText(dl, Fonts.body, x + dot + px(10), ty - px(1), u32(Theme.TEXT, 0.85f),
            Widgets.ellipsize(Fonts.body, status, textWidth - dot - px(10)));

        Showcase.Slide slide = this.ui.showcase.current();
        if (slide != null) {
            float frameW = Math.min(width * 0.34f, px(420));
            float frameH = frameW * 0.56f;
            float fx = x + width - frameW;
            float fy = Math.max(y + u * 4, buttonsY - frameH - u * 14);
            Pixel.nine(dl, Pixel.PANEL, fx - u * 4, fy - u * 4, fx + frameW + u * 4, fy + frameH + u * 7, 4, u32(0xFFFFFF));
            this.ui.showcase.draw("hero", fx, fy, frameW, frameH, 0f);
            if (slide.caption() != null && !slide.caption().isBlank()) {
                String caption = slide.credit() == null || slide.credit().isBlank() ? slide.caption() : slide.caption() + "  ·  " + slide.credit();
                Widgets.drawPill(dl, fx + u * 4, fy + u * 4, caption, Theme.TEXT, 0x0E0418, 0.7f, Icons.Icon.IMAGE);
            }
        }

        boolean excited = false;
        ImGui.setCursorScreenPos(playX, buttonsY);
        if (this.ui.currentAccount().isEmpty()) {
            if (Widgets.primary("hero-signin", "Sign in", Icons.Icon.USER, playWidth, buttonHeight, true)) {
                this.ui.startSignIn();
            }
            excited = ImGui.isItemHovered();
        } else if (selected.isEmpty()) {
            if (Widgets.primary("hero-create", "Create", Icons.Icon.PLUS, playWidth, buttonHeight, true)) {
                this.ui.dialogs.openNewInstance();
            }
            excited = ImGui.isItemHovered();
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
                excited = ImGui.isItemHovered() || this.ui.running.containsKey(instance.id);
            }
        }
        this.ui.world.penguin(dl, playX + playWidth + u * 28, horizon + u * 2, excited);

        if (hasPicker) {
            float px0 = rowX;
            float pickerH = buttonHeight - u * 5;
            float py0 = buttonsY + u;
            ImGui.setCursorScreenPos(px0, py0);
            boolean clicked = ImGui.invisibleButton("hero-picker", pickerWidth, pickerH);
            boolean hovered = ImGui.isItemHovered();
            if (hovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            float hv = Motion.hover("hero-picker#hover", hovered || ImGui.isPopupOpen("hero-instances"));
            Pixel.card(dl, px0, py0, px0 + pickerWidth, py0 + pickerH, hv, u32(Theme.SURFACE));
            float tile = pickerH - u * 12;
            float tileY = py0 + (pickerH - u * 3 - tile) * 0.5f;
            InstancesPage.drawTile(dl, selected.get(), px0 + u * 6, tileY, tile);
            float labelX = px0 + u * 6 + tile + u * 6;
            float labelWidth = pickerWidth - (labelX - px0) - u * 16;
            PixelText.draw(dl, labelX, tileY + u, "INSTANCE", PixelText.small(), u32(Theme.FAINT));
            Widgets.drawText(dl, Fonts.label, labelX, tileY + u + PixelText.height(PixelText.small()) + u * 3, u32(Theme.TEXT),
                Widgets.ellipsize(Fonts.label, selected.get().name, labelWidth));
            Icons.draw(dl, Icons.Icon.CHEVRON_DOWN, px0 + pickerWidth - u * 13, py0 + (pickerH - u * 3 - px(16)) * 0.5f, px(16), u32(Theme.MUTED));
            if (clicked) {
                ImGui.openPopup("hero-instances");
            }
            ImGui.setNextWindowPos(px0, py0 - px(8), 0, 0f, 1f);
            ImGui.setNextWindowSize(pickerWidth + u * 6 + playWidth, 0);
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

    private static java.util.List<String> wrap(final String text, final float scale, final float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (PixelText.width(candidate, scale) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.size() > 2) {
            java.util.List<String> kept = new java.util.ArrayList<>(lines.subList(0, 2));
            kept.set(1, PixelText.fit(kept.get(1) + " " + String.join(" ", lines.subList(2, lines.size())), scale, maxWidth));
            return kept;
        }
        return lines;
    }
    private void drawNews(final float width) {
        List<Feed.News> news = this.ui.feed.news;
        if (news.isEmpty()) {
            if (Widgets.beginCard("home-news-empty", width, 0)) {
                Widgets.textWrapped(Fonts.body, Theme.MUTED, "News from the ABNW team will show up here.");
            }
            Widgets.endCard();
            return;
        }
        int count = Math.min(3, news.size());
        float gap = px(16);
        float column = (width - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            Feed.News item = news.get(i);
            if (i > 0) {
                ImGui.sameLine(0, gap);
            }
            if (this.compactNews("home-news-" + i, item, column)) {
                if (!item.url.isEmpty()) {
                    Desktop.browse(item.url);
                } else {
                    this.ui.navigate(LauncherUi.Page.NEWS);
                }
            }
        }
    }
    private boolean compactNews(final String id, final Feed.News item, final float width) {
        float pad = px(16);
        float thumbW = px(68);
        float thumbH = px(68);
        float h = thumbH + pad * 2 + Fonts.small.size() * 2 + px(10);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, width, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover(id + "#hover", hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.card(dl, x, y, x + width, y + h, hv, u32(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv)));
        float tx = x + pad;
        float ty = y + pad;
        ImageCache.Texture image = item.image.isEmpty() ? null : this.ui.images.get(item.image, 512);
        if (image != null) {
            Showcase.drawCover(dl, image, tx, ty, thumbW, thumbH, 0f, 1f, 0.5f);
        } else {
            dl.addRectFilledMultiColor(tx, ty, tx + thumbW, ty + thumbH, u32(Theme.EMBER_DEEP), u32(Theme.MAROON), u32(Theme.EMBER_DEEP), u32(0x1A0B26));
            Pixel.frame(dl, tx, ty, tx + thumbW, ty + thumbH, u32(Theme.SURFACE), px(3));
            float iconSize = px(28);
            Icons.draw(dl, iconFor(item.tag), tx + (thumbW - iconSize) * 0.5f, ty + (thumbH - iconSize) * 0.5f, iconSize, u32(Theme.EMBER, 0.85f));
        }
        float bx = tx + thumbW + px(16);
        float bw = x + width - pad - bx;
        String meta = (item.tag.isEmpty() ? "" : item.tag.toUpperCase()) + (item.tag.isEmpty() || item.date.isEmpty() ? "" : "  ·  ")
            + Format.date(item.date);
        PixelText.draw(dl, bx, ty + px(2), PixelText.fit(meta.toUpperCase(), PixelText.small(), bw), PixelText.small(), u32(Theme.SUN));
        float titleY = ty + PixelText.height(PixelText.small()) + px(10);
        Widgets.drawText(dl, Fonts.label, bx, titleY, u32(Theme.TEXT), Widgets.ellipsize(Fonts.label, item.title, bw));
        float summaryY = ty + thumbH + px(10);
        float summaryW = width - pad * 2;
        List<String> lines = Widgets.clampLines(Fonts.small, item.summary, summaryW, 2);
        for (String line : lines) {
            Widgets.drawText(dl, Fonts.small, tx, summaryY, u32(Theme.MUTED), line);
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
        Pixel.rect(dl, x, y, x + width, y + h, u32(Theme.BG));
        Pixel.frame(dl, x, y, x + width, y + h, u32(Theme.BORDER_SOFT), px(1));
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
                Pixel.dot(dl, dx + dot * 0.5f, y + pad + dot * 0.5f, dot * 0.5f, u32(i == index ? Theme.SUN : Theme.FAINT, i == index ? 0.8f : 0.4f));
                dx += dot + px(4);
            }
        }
        ImGui.dummy(width, h);
    }
}
