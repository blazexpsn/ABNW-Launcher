package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class PixelText {
    private static final String ATLAS = "resource:/assets/ui/font.png";
    private static final int COLUMNS = 16;
    private static final int GLYPH_HEIGHT = 7;

    private static int cellW = 12;
    private static int cellH = 12;
    private static int first = 32;
    private static int[] advances = new int[0];

    static {
        try (InputStream in = PixelText.class.getResourceAsStream("/assets/ui/font.txt")) {
            if (in != null) {
                String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().split("\\R");
                String[] head = lines[0].trim().split("\\s+");
                cellW = Integer.parseInt(head[0]);
                cellH = Integer.parseInt(head[1]);
                first = Integer.parseInt(head[2]);
                String[] values = lines[1].trim().split("\\s+");
                advances = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    advances[i] = Integer.parseInt(values[i]);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read the pixel font metrics: " + e.getMessage());
        }
    }

    private PixelText() {
    }

    static float small() {
        return Pixel.unit();
    }

    static float title() {
        return Math.round(Pixel.unit() * 1.5f);
    }

    static float headline() {
        return Pixel.unit() * 3f;
    }

    static float height(final float scale) {
        return GLYPH_HEIGHT * scale;
    }

    private static int index(final char c) {
        int i = (c == '·' ? '~' : c) - first;
        if (i < 0 || i >= advances.length) {
            i = '?' - first;
        }
        return i;
    }

    static float width(final String text, final float scale) {
        if (text == null || text.isEmpty() || advances.length == 0) {
            return 0f;
        }
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += advances[index(text.charAt(i))];
        }
        return (w - 1) * scale;
    }

    static float draw(final ImDrawList dl, final float x, final float y, final String text, final float scale, final int color) {
        ImageCache.Texture atlas = Pixel.texture(ATLAS);
        if (atlas == null || advances.length == 0 || text == null) {
            Widgets.drawText(dl, Fonts.label, x, y, color, text == null ? "" : text);
            return Widgets.textWidth(Fonts.label, text == null ? "" : text);
        }
        float tw = atlas.width();
        float th = atlas.height();
        float cx = Math.round(x);
        float cy = Math.round(y);
        for (int i = 0; i < text.length(); i++) {
            int glyph = index(text.charAt(i));
            if (text.charAt(i) != ' ') {
                float u = (glyph % COLUMNS) * cellW;
                float v = (glyph / COLUMNS) * cellH;
                float qx = cx - 2 * scale;
                float qy = cy - 2 * scale;
                dl.addImage(atlas.id(), qx, qy, qx + cellW * scale, qy + cellH * scale, u / tw, v / th, (u + cellW) / tw, (v + cellH) / th, color);
            }
            cx += advances[glyph] * scale;
        }
        return cx - x - scale;
    }

    static float draw(final ImDrawList dl, final float x, final float y, final String text, final float scale, final int rgb, final float alpha) {
        return draw(dl, x, y, text, scale, u32(rgb, alpha));
    }

    static String fit(final String text, final float scale, final float maxWidth) {
        if (text == null || width(text, scale) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && width(cut + "..", scale) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "..";
    }
}
