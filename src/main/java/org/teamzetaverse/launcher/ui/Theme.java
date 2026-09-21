package org.teamzetaverse.launcher.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

final class Theme {
    static final int BG = 0x1A0B26;
    static final int SIDEBAR = 0x150820;
    static final int SURFACE = 0x2B1240;
    static final int SURFACE_HI = 0x37175A;
    static final int SURFACE_HOVER = 0x451D6E;
    static final int BORDER = 0x561A83;
    static final int BORDER_SOFT = 0x3A1458;
    static final int TEXT = 0xF4EEF7;
    static final int MUTED = 0xC9A6DA;
    static final int FAINT = 0x8E6BA3;
    static final int EMBER = 0xB423EC;
    static final int EMBER_HI = 0xC95BF2;
    static final int EMBER_LO = 0x7C2ABA;
    static final int EMBER_DEEP = 0x3B0F5A;
    static final int SUN = 0xE3A2FD;
    static final int MAROON = 0x2A0D3D;
    static final int OK = 0x7FE08A;
    static final int WARN = 0xFFC857;
    static final int ERROR = 0xFF5D7A;
    static final int INFO = 0x8FC5FF;
    static final int ON_EMBER = 0xFFFFFF;

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
        style.setWindowRounding(0);
        style.setChildRounding(0);
        style.setFrameRounding(0);
        style.setPopupRounding(0);
        style.setScrollbarRounding(0);
        style.setGrabRounding(0);
        style.setTabRounding(0);
        style.setWindowBorderSize(0);
        style.setChildBorderSize(1);
        style.setPopupBorderSize(1);
        style.setFrameBorderSize(1);
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
        set(style, ImGuiCol.FrameBgActive, 0x52208A, 1f);
        set(style, ImGuiCol.TitleBg, SURFACE, 1f);
        set(style, ImGuiCol.TitleBgActive, SURFACE, 1f);
        set(style, ImGuiCol.TitleBgCollapsed, SURFACE, 1f);
        set(style, ImGuiCol.MenuBarBg, SIDEBAR, 1f);
        set(style, ImGuiCol.ScrollbarBg, 0, 0f);
        set(style, ImGuiCol.ScrollbarGrab, BORDER, 1f);
        set(style, ImGuiCol.ScrollbarGrabHovered, 0x6A2AA0, 1f);
        set(style, ImGuiCol.ScrollbarGrabActive, EMBER_LO, 1f);
        set(style, ImGuiCol.CheckMark, EMBER, 1f);
        set(style, ImGuiCol.SliderGrab, EMBER, 1f);
        set(style, ImGuiCol.SliderGrabActive, EMBER_HI, 1f);
        set(style, ImGuiCol.Button, SURFACE_HI, 1f);
        set(style, ImGuiCol.ButtonHovered, SURFACE_HOVER, 1f);
        set(style, ImGuiCol.ButtonActive, 0x52208A, 1f);
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
        set(style, ImGuiCol.ModalWindowDimBg, 0x0A0312, 0.72f);

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
