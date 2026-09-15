package org.teamzetaverse.launcher.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

final class Theme {
    static final int BG = 0x0F0B0D;
    static final int SIDEBAR = 0x140E11;
    static final int SURFACE = 0x1C1418;
    static final int SURFACE_HI = 0x261B20;
    static final int SURFACE_HOVER = 0x30222A;
    static final int BORDER = 0x36262D;
    static final int BORDER_SOFT = 0x2A1E23;
    static final int TEXT = 0xFBF3EE;
    static final int MUTED = 0xB9A59D;
    static final int FAINT = 0x7C6960;
    static final int EMBER = 0xFF6414;
    static final int EMBER_HI = 0xFF8440;
    static final int EMBER_LO = 0xE14E05;
    static final int EMBER_DEEP = 0x3A1508;
    static final int SUN = 0xF7E36A;
    static final int MAROON = 0x4A0F0C;
    static final int OK = 0x6BD68F;
    static final int WARN = 0xFFB547;
    static final int ERROR = 0xFF5D6C;
    static final int INFO = 0x8DB8FF;
    static final int ON_EMBER = 0x1E0A03;

    static float scale = 1f;

    private Theme() {
    }

    static float px(final float value) {
        return value * scale;
    }

    static int u32(final int rgb, final float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    static int u32(final int rgb) {
        return u32(rgb, 1f);
    }

    static int mix(final int from, final int to, final float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (r << 16) | (g << 8) | b;
    }

    static float[] rgba(final int rgb, final float alpha) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha};
    }

    static void apply(final float uiScale) {
        scale = uiScale;
        ImGui.styleColorsDark();
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(16);
        style.setChildRounding(16);
        style.setFrameRounding(10);
        style.setPopupRounding(14);
        style.setScrollbarRounding(10);
        style.setGrabRounding(10);
        style.setTabRounding(10);
        style.setWindowBorderSize(0);
        style.setChildBorderSize(1);
        style.setPopupBorderSize(1);
        style.setFrameBorderSize(0);
        style.setTabBorderSize(0);
        style.setWindowPadding(24, 22);
        style.setFramePadding(14, 9);
        style.setItemSpacing(12, 10);
        style.setItemInnerSpacing(10, 6);
        style.setCellPadding(10, 8);
        style.setScrollbarSize(10);
        style.setGrabMinSize(14);
        style.setIndentSpacing(18);
        style.setWindowTitleAlign(0f, 0.5f);
        style.setSeparatorTextBorderSize(1);
        style.setSeparatorTextPadding(0, 6);
        style.setDisabledAlpha(0.45f);

        set(style, ImGuiCol.Text, TEXT, 1f);
        set(style, ImGuiCol.TextDisabled, FAINT, 1f);
        set(style, ImGuiCol.WindowBg, BG, 1f);
        set(style, ImGuiCol.ChildBg, SURFACE, 0f);
        set(style, ImGuiCol.PopupBg, SURFACE, 1f);
        set(style, ImGuiCol.Border, BORDER, 1f);
        set(style, ImGuiCol.BorderShadow, 0, 0f);
        set(style, ImGuiCol.FrameBg, SURFACE_HI, 1f);
        set(style, ImGuiCol.FrameBgHovered, SURFACE_HOVER, 1f);
        set(style, ImGuiCol.FrameBgActive, 0x3A2932, 1f);
        set(style, ImGuiCol.TitleBg, SURFACE, 1f);
        set(style, ImGuiCol.TitleBgActive, SURFACE, 1f);
        set(style, ImGuiCol.TitleBgCollapsed, SURFACE, 1f);
        set(style, ImGuiCol.MenuBarBg, SIDEBAR, 1f);
        set(style, ImGuiCol.ScrollbarBg, 0, 0f);
        set(style, ImGuiCol.ScrollbarGrab, BORDER, 1f);
        set(style, ImGuiCol.ScrollbarGrabHovered, 0x4A3740, 1f);
        set(style, ImGuiCol.ScrollbarGrabActive, EMBER_LO, 1f);
        set(style, ImGuiCol.CheckMark, EMBER, 1f);
        set(style, ImGuiCol.SliderGrab, EMBER, 1f);
        set(style, ImGuiCol.SliderGrabActive, EMBER_HI, 1f);
        set(style, ImGuiCol.Button, SURFACE_HI, 1f);
        set(style, ImGuiCol.ButtonHovered, SURFACE_HOVER, 1f);
        set(style, ImGuiCol.ButtonActive, 0x3A2932, 1f);
        set(style, ImGuiCol.Header, EMBER, 0.14f);
        set(style, ImGuiCol.HeaderHovered, EMBER, 0.22f);
        set(style, ImGuiCol.HeaderActive, EMBER, 0.32f);
        set(style, ImGuiCol.Separator, BORDER_SOFT, 1f);
        set(style, ImGuiCol.SeparatorHovered, EMBER_LO, 1f);
        set(style, ImGuiCol.SeparatorActive, EMBER, 1f);
        set(style, ImGuiCol.ResizeGrip, 0, 0f);
        set(style, ImGuiCol.ResizeGripHovered, EMBER, 0.4f);
        set(style, ImGuiCol.ResizeGripActive, EMBER, 0.7f);
        set(style, ImGuiCol.Tab, SURFACE_HI, 0f);
        set(style, ImGuiCol.TabHovered, SURFACE_HOVER, 1f);
        set(style, ImGuiCol.TabSelected, SURFACE_HI, 1f);
        set(style, ImGuiCol.TabSelectedOverline, EMBER, 1f);
        set(style, ImGuiCol.TabDimmed, SURFACE_HI, 0f);
        set(style, ImGuiCol.TabDimmedSelected, SURFACE_HI, 1f);
        set(style, ImGuiCol.PlotLines, MUTED, 1f);
        set(style, ImGuiCol.PlotHistogram, EMBER, 1f);
        set(style, ImGuiCol.TableHeaderBg, SURFACE_HI, 1f);
        set(style, ImGuiCol.TableBorderStrong, BORDER, 1f);
        set(style, ImGuiCol.TableBorderLight, BORDER_SOFT, 1f);
        set(style, ImGuiCol.TableRowBg, 0, 0f);
        set(style, ImGuiCol.TableRowBgAlt, 0xFFFFFF, 0.02f);
        set(style, ImGuiCol.TextSelectedBg, EMBER, 0.35f);
        set(style, ImGuiCol.DragDropTarget, SUN, 0.9f);
        set(style, ImGuiCol.NavCursor, EMBER, 0.8f);
        set(style, ImGuiCol.ModalWindowDimBg, 0x07040A, 0.72f);

        if (uiScale != 1f) {
            style.scaleAllSizes(uiScale);
        }
    }

    private static void set(final ImGuiStyle style, final int slot, final int rgb, final float alpha) {
        float[] c = rgba(rgb, alpha);
        style.setColor(slot, c[0], c[1], c[2], c[3]);
    }

    static void pushText(final int rgb) {
        float[] c = rgba(rgb, 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], c[3]);
    }
}
