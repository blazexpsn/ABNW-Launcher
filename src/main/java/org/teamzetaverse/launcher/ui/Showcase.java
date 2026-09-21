package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiMouseCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class Showcase {
    record Slide(String source, String caption, String credit) {
    }

    static final String FALLBACK = "resource:/assets/logo.png";
    private static final double INTERVAL = 7.0;
    private static final double FADE = 0.9;

    private final ImageCache images;
    private List<Slide> slides = List.of();
    private int index;
    private int previous = -1;
    private double changedAt;
    private double shownSince;

    Showcase(final ImageCache images) {
        this.images = images;
    }

    void setSlides(final List<Slide> next) {
        List<Slide> filtered = new ArrayList<>(next);
        if (filtered.equals(this.slides)) {
            return;
        }
        Slide current = this.slides.isEmpty() ? null : this.slides.get(Math.min(this.index, this.slides.size() - 1));
        this.slides = List.copyOf(filtered);
        this.index = 0;
        this.previous = -1;
        for (int i = 0; i < this.slides.size(); i++) {
            if (Objects.equals(this.slides.get(i), current)) {
                this.index = i;
            }
        }
    }

    boolean isAnimating() {
        return this.previous >= 0 && ImGui.getTime() - this.changedAt < FADE;
    }

    Slide current() {
        return this.slides.isEmpty() ? null : this.slides.get(this.index);
    }

    private void go(final int target) {
        if (this.slides.size() < 2) {
            return;
        }
        this.previous = this.index;
        this.index = Math.floorMod(target, this.slides.size());
        this.changedAt = ImGui.getTime();
        this.shownSince = this.changedAt;
    }

    void draw(final String id, final float x, final float y, final float w, final float h, final float rounding) {
        ImDrawList dl = ImGui.getWindowDrawList();
        double now = ImGui.getTime();
        boolean hovered = ImGui.isMouseHoveringRect(x, y, x + w, y + h);
        if (this.shownSince == 0) {
            this.shownSince = now;
        }
        if (!hovered && this.slides.size() > 1 && now - this.shownSince > INTERVAL) {
            ImageCache.Texture next = this.images.get(this.slides.get((this.index + 1) % this.slides.size()).source(), 2048);
            if (next != null || this.images.hasFailed(this.slides.get((this.index + 1) % this.slides.size()).source())) {
                this.go(this.index + 1);
            }
        }

        Pixel.rect(dl, x, y, x + w, y + h, u32(Theme.MAROON));
        ImageCache.Texture fallback = this.images.get(FALLBACK, 0, true);
        ImageCache.Texture current = this.slides.isEmpty() ? null : this.images.get(this.slides.get(this.index).source(), 2048);
        float fade = this.previous < 0 ? 1f : (float)Math.min(1.0, (now - this.changedAt) / FADE);
        if (this.previous >= 0 && fade < 1f) {
            ImageCache.Texture before = this.images.get(this.slides.get(this.previous).source(), 2048);
            drawCover(dl, before != null ? before : fallback, x, y, w, h, rounding, 1f, before == null ? 0.35f : 0.5f);
            drawCover(dl, current != null ? current : fallback, x, y, w, h, rounding, Motion.easeOutCubic(fade), current == null ? 0.35f : 0.5f);
            Motion.keepAlive();
        } else {
            drawCover(dl, current != null ? current : fallback, x, y, w, h, rounding, 1f, current == null ? 0.35f : 0.5f);
        }

        if (this.slides.size() > 1) {
            float arrowsAlpha = Motion.hover(id + "#arrows", hovered);
            if (arrowsAlpha > 0.01f) {
                float size = px(36);
                float cy = y + h * 0.42f - size * 0.5f;
                if (arrow(id + "#prev", Icons.Icon.CHEVRON_LEFT, x + px(14), cy, size, arrowsAlpha)) {
                    this.go(this.index - 1);
                }
                if (arrow(id + "#next", Icons.Icon.CHEVRON_RIGHT, x + w - px(14) - size, cy, size, arrowsAlpha)) {
                    this.go(this.index + 1);
                }
            }
            float dot = px(6);
            float gap = px(6);
            float active = px(18);
            float total = (this.slides.size() - 1) * (dot + gap) + active;
            float dx = x + w - px(20) - total;
            float dy = y + px(20);
            for (int i = 0; i < this.slides.size(); i++) {
                float width = Motion.to(id + "#dot" + i, i == this.index ? active : dot, 14f);
                Pixel.rect(dl, dx, dy, dx + width, dy + dot, u32(i == this.index ? Theme.SUN : 0xFFFFFF, i == this.index ? 0.95f : 0.45f));
                dx += width + gap;
            }
        }
    }

    private static boolean arrow(final String id, final Icons.Icon icon, final float x, final float y, final float size, final float alpha) {
        boolean hovered = ImGui.isMouseHoveringRect(x, y, x + size, y + size);
        ImDrawList dl = ImGui.getWindowDrawList();
        float hv = Motion.hover(id, hovered);
        Pixel.dot(dl, x + size * 0.5f, y + size * 0.5f, size * 0.5f, u32(0x0E0418, (0.45f + 0.25f * hv) * alpha));
        Icons.draw(dl, icon, x + size * 0.22f, y + size * 0.22f, size * 0.56f, u32(0xFFFFFF, alpha));
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            return ImGui.isMouseClicked(0);
        }
        return false;
    }

    static void drawCover(final ImDrawList dl, final ImageCache.Texture texture, final float x, final float y, final float w, final float h,
                          final float rounding, final float alpha, final float focusY) {
        if (texture == null || alpha <= 0f) {
            return;
        }
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
            v0 = Math.max(0f, Math.min(1f - span, focusY - span * 0.5f));
            v1 = v0 + span;
        }
        dl.addImage(texture.id(), x, y, x + w, y + h, u0, v0, u1, v1, u32(0xFFFFFF, alpha));
    }
}
