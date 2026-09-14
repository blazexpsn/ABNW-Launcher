package org.teamzetaverse.launcher.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

final class Theme {
    static final float[] ACCENT = rgb(0xFF6A1A);
    static final float[] ACCENT_HOVER = rgb(0xFF8A3D);
    static final float[] ACCENT_ACTIVE = rgb(0xE0550A);
    static final float[] SUN = rgb(0xF5E663);
    static final float[] MUTED = rgb(0x9C8FA8);
    static final float[] ERROR = rgb(0xFF6B6B);
    static final float[] OK = rgb(0x7EDC8A);

    private Theme() {
    }

    static float[] rgb(final int hex) {
        return new float[]{((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f, 1f};
    }

    static void apply(final float scale) {
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(8);
        style.setChildRounding(8);
        style.setFrameRounding(6);
        style.setPopupRounding(8);
        style.setScrollbarRounding(6);
        style.setGrabRounding(6);
        style.setTabRounding(6);
        style.setWindowBorderSize(0);
        style.setWindowPadding(14, 12);
        style.setFramePadding(10, 6);
        style.setItemSpacing(10, 8);

        color(style, ImGuiCol.WindowBg, 0x1B1622);
        color(style, ImGuiCol.ChildBg, 0x231C2C);
        color(style, ImGuiCol.PopupBg, 0x241D2E);
        color(style, ImGuiCol.Border, 0x3A3046);
        color(style, ImGuiCol.FrameBg, 0x302739);
        color(style, ImGuiCol.FrameBgHovered, 0x3C3147);
        color(style, ImGuiCol.FrameBgActive, 0x463955);
        color(style, ImGuiCol.TitleBgActive, 0x2E2438);
        color(style, ImGuiCol.Header, 0x4A2A1E);
        color(style, ImGuiCol.HeaderHovered, 0x6B3620);
        color(style, ImGuiCol.HeaderActive, 0x8A4020);
        color(style, ImGuiCol.Button, 0x3A2F45);
        color(style, ImGuiCol.ButtonHovered, 0x4A3C58);
        color(style, ImGuiCol.ButtonActive, 0x5A4A6B);
        color(style, ImGuiCol.Tab, 0x2E2438);
        color(style, ImGuiCol.TabHovered, 0x6B3620);
        color(style, ImGuiCol.TabSelected, 0x4A2A1E);
        color(style, ImGuiCol.Separator, 0x3A3046);
        color(style, ImGuiCol.TableHeaderBg, 0x2E2438);
        style.setColor(ImGuiCol.CheckMark, ACCENT[0], ACCENT[1], ACCENT[2], 1f);
        style.setColor(ImGuiCol.SliderGrab, ACCENT[0], ACCENT[1], ACCENT[2], 1f);
        style.setColor(ImGuiCol.SliderGrabActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], 1f);
        style.setColor(ImGuiCol.PlotHistogram, ACCENT[0], ACCENT[1], ACCENT[2], 1f);
        style.setColor(ImGuiCol.ModalWindowDimBg, 0.05f, 0.03f, 0.08f, 0.65f);

        if (scale != 1f) {
            style.scaleAllSizes(scale);
        }
    }

    private static void color(final ImGuiStyle style, final int slot, final int hex) {
        float[] c = rgb(hex);
        style.setColor(slot, c[0], c[1], c[2], 1f);
    }

    static boolean accentButton(final String label, final float width, final float height) {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT[0], ACCENT[1], ACCENT[2], 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT_HOVER[0], ACCENT_HOVER[1], ACCENT_HOVER[2], 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT_ACTIVE[0], ACCENT_ACTIVE[1], ACCENT_ACTIVE[2], 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.1f, 0.05f, 0.05f, 1f);
        boolean pressed = ImGui.button(label, width, height);
        ImGui.popStyleColor(4);
        return pressed;
    }

    static void textColored(final float[] color, final String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3]);
        ImGui.textWrapped(text);
        ImGui.popStyleColor();
    }
}
