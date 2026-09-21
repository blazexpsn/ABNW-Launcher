package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;

import imgui.ImDrawList;
import imgui.ImGui;

final class Pixel {
    static final String ICONS = "resource:/assets/ui/icons.png";
    static final String PANEL = "resource:/assets/ui/panel.png";
    static final String PANEL_HOVER = "resource:/assets/ui/panel_hover.png";
    static final String PANEL_ACTIVE = "resource:/assets/ui/panel_active.png";
    static final String PILL = "resource:/assets/ui/pill.png";
    static final String PROGRESS_TRACK = "resource:/assets/ui/progress_track.png";
    static final String PROGRESS_FILL = "resource:/assets/ui/progress_fill.png";
    static final String TOGGLE_OFF = "resource:/assets/ui/toggle_off.png";
    static final String TOGGLE_ON = "resource:/assets/ui/toggle_on.png";
    static final String TOGGLE_KNOB = "resource:/assets/ui/toggle_knob.png";
    static final String STARS = "resource:/assets/ui/stars.png";
    static final String GROUND = "resource:/assets/ui/ground.png";
    static final String BUTTON_DANGER = "resource:/assets/ui/button_danger.png";
    static final String BUTTON_PRIMARY = "resource:/assets/button_primary.png";

    private static final int ICON_CELL = 16;
    private static final int ICON_COLUMNS = 8;

    private static ImageCache images;

    private Pixel() {
    }

    static void useImages(final ImageCache cache) {
        images = cache;
    }

    static ImageCache.Texture texture(final String key) {
        return images == null ? null : images.get(key, 0, true);
    }

    static float unit() {
        return Math.max(2f, Math.round(px(2f)));
    }

    static void rect(final ImDrawList dl, final float x0, final float y0, final float x1, final float y1, final int color) {
        float w = x1 - x0;
        float h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return;
        }
        float n = Math.min(unit(), Math.min(w, h) * 0.25f);
        dl.addRectFilled(x0 + n, y0, x1 - n, y1, color);
        dl.addRectFilled(x0, y0 + n, x0 + n, y1 - n, color);
        dl.addRectFilled(x1 - n, y0 + n, x1, y1 - n, color);
    }

    static void frame(final ImDrawList dl, final float x0, final float y0, final float x1, final float y1, final int color, final float thickness) {
        float w = x1 - x0;
        float h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return;
        }
        float t = Math.max(1f, Math.round(thickness));
        float n = Math.min(Math.max(t, unit() * 0.5f), Math.min(w, h) * 0.25f);
        dl.addRectFilled(x0 + n, y0, x1 - n, y0 + t, color);
        dl.addRectFilled(x0 + n, y1 - t, x1 - n, y1, color);
        dl.addRectFilled(x0, y0 + n, x0 + t, y1 - n, color);
        dl.addRectFilled(x1 - t, y0 + n, x1, y1 - n, color);
    }

    static void dot(final ImDrawList dl, final float cx, final float cy, final float radius, final int color) {
        float r = Math.max(1f, Math.round(radius));
        rect(dl, cx - r, cy - r, cx + r, cy + r, color);
    }

    static boolean sprite(final ImDrawList dl, final String key, final float x0, final float y0, final float x1, final float y1,
                          final float u0, final float v0, final float u1, final float v1, final int tint) {
        ImageCache.Texture texture = texture(key);
        if (texture == null) {
            return false;
        }
        dl.addImage(texture.id(), x0, y0, x1, y1, u0, v0, u1, v1, tint);
        return true;
    }

    static boolean icon(final ImDrawList dl, final int index, final float x, final float y, final float size, final int tint) {
        ImageCache.Texture texture = texture(ICONS);
        if (texture == null) {
            return false;
        }
        float snapped = Math.max(ICON_CELL, Math.round(size / (ICON_CELL * 0.5f)) * (ICON_CELL * 0.5f));
        if (size < ICON_CELL * 0.8f) {
            snapped = Math.max(size, 12f);
        }
        float ox = Math.round(x + (size - snapped) * 0.5f);
        float oy = Math.round(y + (size - snapped) * 0.5f);
        float tw = texture.width();
        float th = texture.height();
        float cu = (index % ICON_COLUMNS) * ICON_CELL;
        float cv = (index / ICON_COLUMNS) * ICON_CELL;
        dl.addImage(texture.id(), ox, oy, ox + snapped, oy + snapped, cu / tw, cv / th, (cu + ICON_CELL) / tw, (cv + ICON_CELL) / th, tint);
        return true;
    }

    static boolean nine(final ImDrawList dl, final String key, final float x0, final float y0, final float x1, final float y1,
                        final int cap, final int tint) {
        ImageCache.Texture texture = texture(key);
        if (texture == null) {
            return false;
        }
        float tw = texture.width();
        float th = texture.height();
        float s = unit();
        float c = Math.min(cap * s, Math.min((x1 - x0), (y1 - y0)) * 0.5f);
        float[] xs = {x0, x0 + c, x1 - c, x1};
        float[] ys = {y0, y0 + c, y1 - c, y1};
        float[] us = {0f, cap / tw, (tw - cap) / tw, 1f};
        float[] vs = {0f, cap / th, (th - cap) / th, 1f};
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                if (xs[i + 1] > xs[i] && ys[j + 1] > ys[j]) {
                    dl.addImage(texture.id(), xs[i], ys[j], xs[i + 1], ys[j + 1], us[i], vs[j], us[i + 1], vs[j + 1], tint);
                }
            }
        }
        return true;
    }

    static boolean three(final ImDrawList dl, final String key, final float x0, final float y0, final float x1, final float y1,
                         final int cap, final int tint) {
        ImageCache.Texture texture = texture(key);
        if (texture == null) {
            return false;
        }
        float tw = texture.width();
        float scale = (y1 - y0) / texture.height();
        float c = Math.min(cap * scale, (x1 - x0) * 0.5f);
        float u0 = cap / tw;
        float u1 = (tw - cap) / tw;
        dl.addImage(texture.id(), x0, y0, x0 + c, y1, 0f, 0f, u0, 1f, tint);
        if (x1 - c > x0 + c) {
            dl.addImage(texture.id(), x0 + c, y0, x1 - c, y1, u0, 0f, u1, 1f, tint);
        }
        dl.addImage(texture.id(), x1 - c, y0, x1, y1, u1, 0f, 1f, 1f, tint);
        return true;
    }

    static boolean ready() {
        return texture(PANEL) != null;
    }

    static void card(final ImDrawList dl, final float x0, final float y0, final float x1, final float y1, final float hover, final int fallback) {
        if (!nine(dl, PANEL, x0, y0, x1, y1, 4, Theme.u32(0xFFFFFF))) {
            rect(dl, x0, y0, x1, y1, fallback);
            return;
        }
        if (hover > 0.01f) {
            nine(dl, PANEL_HOVER, x0, y0, x1, y1, 4, Theme.u32(0xFFFFFF, hover));
        }
    }

    static void highlightCard(final ImDrawList dl, final float x0, final float y0, final float x1, final float y1, final int fallback) {
        if (!nine(dl, PANEL_ACTIVE, x0, y0, x1, y1, 4, Theme.u32(0xFFFFFF))) {
            rect(dl, x0, y0, x1, y1, fallback);
        }
    }

    static void windowPanel() {
        float x = ImGui.getWindowPosX();
        float y = ImGui.getWindowPosY();
        nine(ImGui.getWindowDrawList(), PANEL, x, y, x + ImGui.getWindowWidth(), y + ImGui.getWindowHeight(), 4, Theme.u32(0xFFFFFF));
    }

    static boolean tile(final ImDrawList dl, final String key, final float x0, final float y0, final float x1, final float y1,
                        final float scale, final int tint) {
        ImageCache.Texture texture = texture(key);
        if (texture == null) {
            return false;
        }
        float tw = texture.width() * scale;
        float th = texture.height() * scale;
        dl.pushClipRect(x0, y0, x1, y1, true);
        for (float ty = y0; ty < y1; ty += th) {
            for (float tx = x0; tx < x1; tx += tw) {
                dl.addImage(texture.id(), tx, ty, tx + tw, ty + th, 0f, 0f, 1f, 1f, tint);
            }
        }
        dl.popClipRect();
        return true;
    }

    static boolean strip(final ImDrawList dl, final String key, final float x0, final float y1, final float x1, final float scale, final int tint) {
        ImageCache.Texture texture = texture(key);
        if (texture == null) {
            return false;
        }
        float tw = texture.width() * scale;
        float th = texture.height() * scale;
        dl.pushClipRect(x0, y1 - th, x1, y1, true);
        for (float tx = x0; tx < x1; tx += tw) {
            dl.addImage(texture.id(), tx, y1 - th, tx + tw, y1, 0f, 0f, 1f, 1f, tint);
        }
        dl.popClipRect();
        return true;
    }
}
