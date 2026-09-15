package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

final class Widgets {
    enum Variant {
        PRIMARY, SECONDARY, GHOST, DANGER
    }

    private static final ImVec2 MEASURE = new ImVec2();

    private Widgets() {
    }

    static float textWidth(final Fonts.Face face, final String text) {
        ImGui.pushFont(face.font(), face.size());
        ImGui.calcTextSize(MEASURE, text);
        ImGui.popFont();
        return MEASURE.x;
    }

    static float textHeight(final Fonts.Face face, final String text, final float wrapWidth) {
        ImGui.pushFont(face.font(), face.size());
        ImGui.calcTextSize(MEASURE, text, false, wrapWidth);
        ImGui.popFont();
        return MEASURE.y;
    }

    static void text(final Fonts.Face face, final int rgb, final String text) {
        face.push();
        Theme.pushText(rgb);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
        ImGui.popFont();
    }

    static void textWrapped(final Fonts.Face face, final int rgb, final String text) {
        face.push();
        Theme.pushText(rgb);
        ImGui.pushTextWrapPos(0f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.popStyleColor();
        ImGui.popFont();
    }

    static void drawText(final ImDrawList dl, final Fonts.Face face, final float x, final float y, final int color, final String text) {
        dl.addText(face.font(), (int)face.size(), x, y, color, text);
    }

    static void drawTextWrapped(final ImDrawList dl, final Fonts.Face face, final float x, final float y, final int color, final String text, final float wrap) {
        dl.addText(face.font(), (int)face.size(), x, y, color, text, null, wrap);
    }

    static String ellipsize(final Fonts.Face face, final String text, final float maxWidth) {
        if (text == null) {
            return "";
        }
        if (textWidth(face, text) <= maxWidth) {
            return text;
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (textWidth(face, text.substring(0, mid) + "…") <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + "…";
    }

    static void overline(final String text, final int rgb) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float width = drawOverline(dl, x, y, u32(rgb), text);
        ImGui.dummy(width, Fonts.overline.size());
    }

    static float drawOverline(final ImDrawList dl, final float x, final float y, final int color, final String text) {
        float cursor = x;
        float spacing = px(1.4f);
        String upper = text.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            String ch = upper.substring(i, i + 1);
            drawText(dl, Fonts.overline, cursor, y, color, ch);
            cursor += textWidth(Fonts.overline, ch) + spacing;
        }
        return cursor - x - spacing;
    }

    static boolean button(final String id, final String label, final Icons.Icon icon, final Variant variant, final float width, final float height,
                          final boolean enabled) {
        Fonts.Face face = variant == Variant.PRIMARY ? Fonts.button : Fonts.label;
        float iconSize = px(height >= px(48) ? 20 : 17);
        float gap = label.isEmpty() ? 0 : px(9);
        float textW = label.isEmpty() ? 0 : textWidth(face, label);
        float contentW = textW + (icon != null ? iconSize + gap : 0);
        float h = height > 0 ? height : px(40);
        float w = width > 0 ? width : width < 0 ? ImGui.getContentRegionAvailX() : contentW + px(label.isEmpty() ? 22 : 36);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();

        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = enabled && ImGui.isItemHovered();
        boolean active = enabled && ImGui.isItemActive();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover(id + "#hover", hovered);
        float press = Motion.to(id + "#press", active ? 1f : 0f, 30f);
        float alpha = enabled ? 1f : 0.42f;
        float r = Math.min(px(12), h * 0.5f);
        float inset = press * px(1.2f);
        float x0 = x + inset;
        float y0 = y + inset;
        float x1 = x + w - inset;
        float y1 = y + h - inset;
        ImDrawList dl = ImGui.getWindowDrawList();

        int fg;
        switch (variant) {
            case PRIMARY -> {
                if (hv > 0.01f && enabled) {
                    for (int i = 3; i >= 1; i--) {
                        float grow = px(2.6f) * i * hv;
                        dl.addRectFilled(x0 - grow, y0 - grow, x1 + grow, y1 + grow, u32(Theme.EMBER, 0.07f * hv / i), r + grow);
                    }
                }
                int base = active ? Theme.EMBER_LO : Theme.mix(Theme.EMBER, Theme.EMBER_HI, hv);
                dl.addRectFilled(x0, y0, x1, y1, u32(base, alpha), r);
                dl.addRectFilled(x0, y0, x1, y0 + (y1 - y0) * 0.5f, u32(0xFFFFFF, 0.07f * alpha), r, ImDrawFlags.RoundCornersTop);
                fg = u32(Theme.ON_EMBER, alpha);
            }
            case SECONDARY -> {
                dl.addRectFilled(x0, y0, x1, y1, u32(Theme.mix(Theme.SURFACE_HI, Theme.SURFACE_HOVER, hv), alpha), r);
                dl.addRect(x0, y0, x1, y1, u32(Theme.mix(Theme.BORDER, Theme.EMBER_LO, hv * 0.35f), alpha), r, ImDrawFlags.None, px(1));
                fg = u32(Theme.TEXT, alpha);
            }
            case DANGER -> {
                dl.addRectFilled(x0, y0, x1, y1, u32(Theme.ERROR, (0.12f + 0.12f * hv) * alpha), r);
                fg = u32(Theme.ERROR, alpha);
            }
            default -> {
                dl.addRectFilled(x0, y0, x1, y1, u32(0xFFFFFF, 0.06f * hv * alpha), r);
                fg = u32(Theme.mix(Theme.MUTED, Theme.TEXT, hv), alpha);
            }
        }
        float cx = x + (w - contentW) * 0.5f;
        if (icon != null) {
            Icons.draw(dl, icon, cx, y + (h - iconSize) * 0.5f, iconSize, fg);
            cx += iconSize + gap;
        }
        if (!label.isEmpty()) {
            drawText(dl, face, cx, y + (h - face.size()) * 0.5f - px(0.5f), fg, label);
        }
        return clicked && enabled;
    }

    static boolean primary(final String id, final String label, final Icons.Icon icon, final float width, final float height, final boolean enabled) {
        return button(id, label, icon, Variant.PRIMARY, width, height, enabled);
    }

    static boolean secondary(final String id, final String label, final Icons.Icon icon) {
        return button(id, label, icon, Variant.SECONDARY, 0, 0, true);
    }

    static boolean secondary(final String id, final String label, final Icons.Icon icon, final boolean enabled) {
        return button(id, label, icon, Variant.SECONDARY, 0, 0, enabled);
    }

    static boolean iconButton(final String id, final Icons.Icon icon, final String tooltip) {
        return iconButton(id, icon, px(34), tooltip, true);
    }

    static boolean iconButton(final String id, final Icons.Icon icon, final float size, final String tooltip, final boolean enabled) {
        boolean clicked = button(id, "", icon, Variant.GHOST, size, size, enabled);
        if (tooltip != null && ImGui.isItemHovered()) {
            tooltip(tooltip);
        }
        return clicked;
    }

    static void tooltip(final String text) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(10), px(7));
        ImGui.pushStyleColor(ImGuiCol.PopupBg, Theme.rgba(Theme.SURFACE_HI, 1f)[0], Theme.rgba(Theme.SURFACE_HI, 1f)[1], Theme.rgba(Theme.SURFACE_HI, 1f)[2], 1f);
        ImGui.beginTooltip();
        text(Fonts.small, Theme.TEXT, text);
        ImGui.endTooltip();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }

    static boolean link(final String id, final String label, final Icons.Icon icon) {
        Fonts.Face face = Fonts.labelSmall;
        float iconSize = px(14);
        float w = textWidth(face, label) + (icon != null ? iconSize + px(5) : 0);
        float h = face.size() + px(2);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover(id + "#hover", hovered);
        int color = u32(Theme.mix(Theme.EMBER, Theme.SUN, hv * 0.6f));
        ImDrawList dl = ImGui.getWindowDrawList();
        drawText(dl, face, x, y, color, label);
        if (icon != null) {
            Icons.draw(dl, icon, x + w - iconSize, y + (h - iconSize) * 0.5f, iconSize, color);
        }
        return clicked;
    }

    static float pillWidth(final String text, final Icons.Icon icon) {
        return textWidth(Fonts.tiny, text) + px(18) + (icon != null ? px(17) : 0);
    }

    static void pill(final String text, final int fg, final int bg, final float bgAlpha, final Icons.Icon icon) {
        float h = px(22);
        float w = pillWidth(text, icon);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        drawPill(ImGui.getWindowDrawList(), x, y, text, fg, bg, bgAlpha, icon);
        ImGui.dummy(w, h);
    }

    static void drawPill(final ImDrawList dl, final float x, final float y, final String text, final int fg, final int bg, final float bgAlpha,
                         final Icons.Icon icon) {
        float h = px(22);
        float w = pillWidth(text, icon);
        dl.addRectFilled(x, y, x + w, y + h, u32(bg, bgAlpha), h * 0.5f);
        float cx = x + px(9);
        if (icon != null) {
            Icons.draw(dl, icon, cx, y + (h - px(12)) * 0.5f, px(12), u32(fg));
            cx += px(17);
        }
        drawText(dl, Fonts.tiny, cx, y + (h - Fonts.tiny.size()) * 0.5f - px(0.5f), u32(fg), text);
    }

    static void statusDot(final int rgb) {
        float size = px(8);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - size) * 0.5f;
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addCircleFilled(x + size * 0.5f, y + size * 0.5f, size * 0.95f, u32(rgb, 0.22f));
        dl.addCircleFilled(x + size * 0.5f, y + size * 0.5f, size * 0.5f, u32(rgb));
        ImGui.dummy(size, ImGui.getTextLineHeight());
    }

    static boolean toggle(final String id, final boolean value) {
        float w = px(42);
        float h = px(24);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float t = Motion.to(id + "#on", value ? 1f : 0f, 18f);
        float hv = Motion.hover(id + "#hover", hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        int track = Theme.mix(Theme.mix(Theme.SURFACE_HOVER, 0x3E2E36, hv), Theme.EMBER, t);
        dl.addRectFilled(x, y, x + w, y + h, u32(track), h * 0.5f);
        float knob = h * 0.5f - px(3);
        dl.addCircleFilled(x + h * 0.5f + t * (w - h), y + h * 0.5f, knob, u32(Theme.mix(Theme.MUTED, 0xFFFFFF, t)));
        return clicked;
    }

    static void progress(final float fraction, final float width, final float height) {
        float w = width < 0 ? ImGui.getContentRegionAvailX() : width;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        float r = height * 0.5f;
        dl.addRectFilled(x, y, x + w, y + height, u32(0xFFFFFF, 0.07f), r);
        if (fraction < 0) {
            float t = (float)((ImGui.getTime() % 1.3) / 1.3);
            float segment = w * 0.32f;
            float start = x - segment + (w + segment) * Motion.easeOutCubic(t);
            dl.pushClipRect(x, y, x + w, y + height, true);
            dl.addRectFilled(start, y, start + segment, y + height, u32(Theme.EMBER), r);
            dl.popClipRect();
            Motion.keepAlive();
        } else {
            float shown = Motion.to("progress#" + Math.round(x) + ":" + Math.round(y), fraction, 10f);
            float fill = Math.max(height, w * Math.min(1f, shown));
            dl.addRectFilled(x, y, x + fill, y + height, u32(Theme.EMBER), r);
            dl.addCircleFilled(x + fill - r, y + r, r * 0.55f, u32(Theme.SUN, 0.85f));
        }
        ImGui.dummy(w, height);
    }

    static boolean beginCard(final String id, final float width, final float height) {
        return beginCard(id, width, height, Theme.SURFACE, px(20), px(18));
    }

    static boolean beginCard(final String id, final float width, final float height, final int background, final float padX, final float padY) {
        float[] bg = Theme.rgba(background, 1f);
        float[] border = Theme.rgba(Theme.BORDER_SOFT, 1f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg[0], bg[1], bg[2], bg[3]);
        ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], border[3]);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padX, padY);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, px(16));
        int childFlags = ImGuiChildFlags.Borders | ImGuiChildFlags.AlwaysUseWindowPadding | (height <= 0 ? ImGuiChildFlags.AutoResizeY : 0);
        int windowFlags = height <= 0 ? ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse : 0;
        boolean open = ImGui.beginChild(id, width, Math.max(0, height), childFlags, windowFlags);
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(2);
        return open;
    }

    static void endCard() {
        ImGui.endChild();
    }

    static void pageHeader(final String title, final String subtitle) {
        text(Fonts.title, Theme.TEXT, title);
        if (subtitle != null && !subtitle.isEmpty()) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() - px(4));
            text(Fonts.body, Theme.MUTED, subtitle);
        }
        ImGui.dummy(0, px(8));
    }

    static void cardTitle(final Icons.Icon icon, final String title) {
        if (icon != null) {
            float size = px(18);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            Icons.draw(ImGui.getWindowDrawList(), icon, x, y + (Fonts.heading.size() - size) * 0.5f, size, u32(Theme.EMBER));
            ImGui.dummy(size, Fonts.heading.size());
            ImGui.sameLine(0, px(10));
        }
        text(Fonts.heading, Theme.TEXT, title);
        ImGui.dummy(0, px(2));
    }

    static void alignRight(final float width) {
        float avail = ImGui.getContentRegionAvailX();
        if (avail > width) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - width);
        }
    }

    static void divider() {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + px(6);
        float w = ImGui.getContentRegionAvailX();
        ImGui.getWindowDrawList().addLine(x, y, x + w, y, u32(Theme.BORDER_SOFT), px(1));
        ImGui.dummy(w, px(13));
    }

    static void fieldLabel(final String label, final String help) {
        text(Fonts.label, Theme.TEXT, label);
        if (help != null && !help.isEmpty()) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
            textWrapped(Fonts.small, Theme.FAINT, help);
        }
    }

    static java.util.List<String> clampLines(final Fonts.Face face, final String text, final float width, final int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (textWidth(face, candidate) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
                i++;
            } else {
                if (lines.size() == maxLines - 1) {
                    break;
                }
                lines.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0) {
            String last = line.toString();
            if (i < words.length) {
                last = ellipsize(face, last + " " + String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), width);
            } else {
                last = ellipsize(face, last, width);
            }
            lines.add(last);
        }
        return lines;
    }

    static int segmented(final String id, final String[] labels, final int selected, final float width) {
        float h = px(38);
        float w = width < 0 ? ImGui.getContentRegionAvailX() : width;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h, u32(Theme.SURFACE_HI), px(11));
        float segment = (w - px(8)) / labels.length;
        float pos = Motion.to(id + "#pos", selected, 16f);
        float sx = x + px(4) + pos * segment;
        dl.addRectFilled(sx, y + px(4), sx + segment, y + h - px(4), u32(Theme.EMBER, 0.18f), px(8));
        dl.addRect(sx, y + px(4), sx + segment, y + h - px(4), u32(Theme.EMBER, 0.55f), px(8), ImDrawFlags.None, px(1));
        int result = selected;
        for (int i = 0; i < labels.length; i++) {
            float bx = x + px(4) + i * segment;
            ImGui.setCursorScreenPos(bx, y + px(4));
            if (ImGui.invisibleButton(id + "#" + i, segment, h - px(8))) {
                result = i;
            }
            boolean hovered = ImGui.isItemHovered();
            if (hovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            float tw = textWidth(Fonts.labelSmall, labels[i]);
            int color = i == selected ? u32(Theme.TEXT) : u32(hovered ? Theme.TEXT : Theme.MUTED);
            drawText(dl, Fonts.labelSmall, bx + (segment - tw) * 0.5f, y + (h - Fonts.labelSmall.size()) * 0.5f - px(0.5f), color, labels[i]);
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(w, h);
        return result;
    }

    static void beginField() {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, px(12), px(10));
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, px(10));
    }

    static void endField() {
        ImGui.popStyleVar(2);
    }
}
