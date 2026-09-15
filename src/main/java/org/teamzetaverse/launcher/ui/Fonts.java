package org.teamzetaverse.launcher.ui;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiFreeTypeLoaderFlags;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class Fonts {
    record Face(ImFont font, float size) {
        void push() {
            ImGui.pushFont(this.font, this.size);
        }
    }

    private static final short[] RANGES = {0x0020, 0x00FF, 0x2010, 0x2027, 0x2030, 0x203A, 0x2190, 0x2193, 0x2212, 0x2212, 0};
    private static final List<byte[]> KEEP_ALIVE = new ArrayList<>();

    static Face body;
    static Face small;
    static Face tiny;
    static Face label;
    static Face labelSmall;
    static Face button;
    static Face overline;
    static Face heading;
    static Face title;
    static Face hero;
    static Face mono;

    private Fonts() {
    }

    static void load(final ImGuiIO io, final float scale) {
        ImFontAtlas atlas = io.getFonts();
        atlas.setFreeTypeRenderer(true);
        byte[] regular = read("PlusJakartaSans-Regular.ttf");
        byte[] semibold = read("PlusJakartaSans-SemiBold.ttf");
        byte[] extrabold = read("PlusJakartaSans-ExtraBold.ttf");
        byte[] display = read("SpaceGrotesk-Bold.ttf");
        byte[] displayMedium = read("SpaceGrotesk-Medium.ttf");
        byte[] monoData = read("JetBrainsMono-Regular.ttf");

        body = add(atlas, regular, 15f, scale);
        small = add(atlas, regular, 13f, scale);
        tiny = add(atlas, semibold, 11.5f, scale);
        label = add(atlas, semibold, 15f, scale);
        labelSmall = add(atlas, semibold, 13f, scale);
        button = add(atlas, extrabold, 15.5f, scale);
        overline = add(atlas, extrabold, 11.5f, scale);
        heading = add(atlas, displayMedium, 19f, scale);
        title = add(atlas, display, 28f, scale);
        hero = add(atlas, display, 46f, scale);
        mono = add(atlas, monoData, 13f, scale);
        io.setFontDefault(body.font());
    }

    private static Face add(final ImFontAtlas atlas, final byte[] data, final float size, final float scale) {
        float pixels = Math.round(size * scale);
        if (data == null) {
            ImFontConfig fallback = new ImFontConfig();
            fallback.setSizePixels(pixels);
            ImFont font = atlas.addFontDefaultVector(fallback);
            fallback.destroy();
            return new Face(font, pixels);
        }
        ImFontConfig config = new ImFontConfig();
        config.setFontLoaderFlags(ImGuiFreeTypeLoaderFlags.LightHinting);
        config.setPixelSnapH(true);
        config.setOversampleH(1);
        config.setOversampleV(1);
        ImFont font = atlas.addFontFromMemoryTTF(data, pixels, config, RANGES);
        config.destroy();
        return new Face(font, pixels);
    }

    private static byte[] read(final String name) {
        try (InputStream in = Fonts.class.getResourceAsStream("/assets/fonts/" + name)) {
            if (in == null) {
                System.err.println("Missing font " + name);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            KEEP_ALIVE.add(bytes);
            return bytes;
        } catch (IOException e) {
            System.err.println("Could not read font " + name + ": " + e.getMessage());
            return null;
        }
    }
}
