package org.teamzetaverse.launcher.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;

final class Icons {
    enum Icon {
        HOME, INSTANCES, NEWS, SETTINGS, PLAY, STOP, PLUS, IMPORT, EXPORT, FOLDER, TRASH, EDIT, USER, CLOSE,
        CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN, REFRESH, CHECK, TERMINAL, SPARK, MEGAPHONE, CLOCK, IMAGE,
        LOGOUT, EXTERNAL, ALERT, COPY, CHIP, COFFEE, WRENCH, CUBE, DISCORD_WAVE, DOWNLOAD, SWAP, CROWN, HEART, COSMETICS, UPDATE, SOON, PENGUIN_OK, PENGUIN_WARN, PENGUIN_NEWS, PENGUIN_EMPTY, PENGUIN_BOX,
        PENGUIN_PLAY, PENGUIN_KEY, PENGUIN_ALERT, PENGUIN_CLOCK, PENGUIN_SUPPORTER, PENGUIN_HAPPY
    }

    private static final float PI = (float)Math.PI;

    private Icons() {
    }

    static void inline(final Icon icon, final float size, final int color) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float offset = (ImGui.getTextLineHeight() - size) * 0.5f;
        draw(ImGui.getWindowDrawList(), icon, x, y + Math.max(0, offset), size, color);
        ImGui.dummy(size, Math.max(size, ImGui.getTextLineHeight()));
    }

    static boolean isPenguin(final Icon icon) {
        return World.penguinSprite(icon) != null;
    }

    static void draw(final ImDrawList dl, final Icon icon, final float x, final float y, final float size, final int color) {
        String penguin = World.penguinSprite(icon);
        if (penguin != null) {
            World.penguinHolding(dl, penguin, x, y, size);
            return;
        }
        if (Pixel.icon(dl, icon.ordinal(), x, y, size, color)) {
            return;
        }
        final float u = size / 24f;
        final float t = Math.max(1.4f, size / 12.5f);
        switch (icon) {
            case HOME -> {
                poly(dl, x, y, u, color, t, false, 3.5f, 11.5f, 12, 4, 20.5f, 11.5f);
                poly(dl, x, y, u, color, t, false, 6, 10, 6, 20, 18, 20, 18, 10);
                poly(dl, x, y, u, color, t, false, 10, 20, 10, 14.5f, 14, 14.5f, 14, 20);
            }
            case INSTANCES -> {
                poly(dl, x, y, u, color, t, true, 12, 3, 21, 7.8f, 12, 12.6f, 3, 7.8f);
                poly(dl, x, y, u, color, t, false, 3, 12.2f, 12, 17, 21, 12.2f);
                poly(dl, x, y, u, color, t, false, 3, 16.6f, 12, 21.4f, 21, 16.6f);
            }
            case NEWS -> {
                rect(dl, x, y, u, color, t, 3, 4.5f, 21, 19.5f, 2.5f);
                line(dl, x, y, u, color, t, 7, 9, 12, 9);
                line(dl, x, y, u, color, t, 7, 12.5f, 17, 12.5f);
                line(dl, x, y, u, color, t, 7, 16, 14.5f, 16);
                dl.addRectFilled(x + 14.5f * u, y + 7.8f * u, x + 17.2f * u, y + 10.2f * u, color, u);
            }
            case SETTINGS -> {
                dl.addCircle(x + 12 * u, y + 12 * u, 3.1f * u, color, 0, t);
                dl.addCircle(x + 12 * u, y + 12 * u, 6.8f * u, color, 0, t);
                for (int i = 0; i < 8; i++) {
                    float a = i * PI / 4f;
                    float cos = (float)Math.cos(a);
                    float sin = (float)Math.sin(a);
                    dl.addLine(x + (12 + cos * 6.8f) * u, y + (12 + sin * 6.8f) * u, x + (12 + cos * 9.6f) * u, y + (12 + sin * 9.6f) * u, color, t * 1.7f);
                }
            }
            case PLAY -> {
                dl.pathClear();
                dl.pathLineTo(x + 8 * u, y + 5 * u);
                dl.pathLineTo(x + 19.5f * u, y + 12 * u);
                dl.pathLineTo(x + 8 * u, y + 19 * u);
                dl.pathFillConvex(color);
            }
            case STOP -> dl.addRectFilled(x + 6.5f * u, y + 6.5f * u, x + 17.5f * u, y + 17.5f * u, color, 2.5f * u);
            case PLUS -> {
                line(dl, x, y, u, color, t, 12, 5, 12, 19);
                line(dl, x, y, u, color, t, 5, 12, 19, 12);
            }
            case IMPORT, DOWNLOAD -> {
                line(dl, x, y, u, color, t, 12, 3.5f, 12, 14.5f);
                poly(dl, x, y, u, color, t, false, 7.5f, 10, 12, 14.5f, 16.5f, 10);
                poly(dl, x, y, u, color, t, false, 4, 15.5f, 4, 20, 20, 20, 20, 15.5f);
            }
            case EXPORT -> {
                line(dl, x, y, u, color, t, 12, 14.5f, 12, 3.5f);
                poly(dl, x, y, u, color, t, false, 7.5f, 8, 12, 3.5f, 16.5f, 8);
                poly(dl, x, y, u, color, t, false, 4, 15.5f, 4, 20, 20, 20, 20, 15.5f);
            }
            case FOLDER -> poly(dl, x, y, u, color, t, true, 3, 6, 9.5f, 6, 11.5f, 8.5f, 21, 8.5f, 21, 19, 3, 19);
            case TRASH -> {
                line(dl, x, y, u, color, t, 4, 7, 20, 7);
                poly(dl, x, y, u, color, t, false, 9, 7, 9, 4, 15, 4, 15, 7);
                poly(dl, x, y, u, color, t, false, 6, 7, 7, 20, 17, 20, 18, 7);
                line(dl, x, y, u, color, t, 10, 11, 10, 16);
                line(dl, x, y, u, color, t, 14, 11, 14, 16);
            }
            case EDIT -> {
                poly(dl, x, y, u, color, t, true, 4, 20, 5, 15.5f, 15.5f, 5, 19, 8.5f, 8.5f, 19);
                line(dl, x, y, u, color, t, 13, 7.5f, 16.5f, 11);
            }
            case USER -> {
                dl.addCircle(x + 12 * u, y + 8.5f * u, 3.6f * u, color, 0, t);
                dl.pathClear();
                dl.pathArcTo(x + 12 * u, y + 21 * u, 7.5f * u, PI, PI * 2f, 16);
                dl.pathStroke(color, 0, t);
            }
            case CLOSE -> {
                line(dl, x, y, u, color, t, 6.5f, 6.5f, 17.5f, 17.5f);
                line(dl, x, y, u, color, t, 17.5f, 6.5f, 6.5f, 17.5f);
            }
            case CHEVRON_LEFT -> poly(dl, x, y, u, color, t, false, 15, 5, 8, 12, 15, 19);
            case CHEVRON_RIGHT -> poly(dl, x, y, u, color, t, false, 9, 5, 16, 12, 9, 19);
            case CHEVRON_DOWN -> poly(dl, x, y, u, color, t, false, 6, 9, 12, 15, 18, 9);
            case REFRESH -> {
                dl.pathClear();
                dl.pathArcTo(x + 12 * u, y + 12 * u, 7.5f * u, -PI * 0.3f, PI * 1.45f, 24);
                dl.pathStroke(color, 0, t);
                poly(dl, x, y, u, color, t, false, 14.5f, 5.2f, 18.6f, 7.6f, 17.2f, 3);
            }
            case CHECK -> poly(dl, x, y, u, color, t * 1.15f, false, 5, 12.5f, 10, 17.5f, 19, 7);
            case TERMINAL -> {
                rect(dl, x, y, u, color, t, 3, 4.5f, 21, 19.5f, 2.5f);
                poly(dl, x, y, u, color, t, false, 7, 9.5f, 10, 12, 7, 14.5f);
                line(dl, x, y, u, color, t, 12, 15, 17, 15);
            }
            case SPARK -> {
                star(dl, x + 11 * u, y + 13 * u, 9 * u, 2.2f * u, color);
                star(dl, x + 19 * u, y + 5 * u, 3.4f * u, 0.9f * u, color);
            }
            case MEGAPHONE -> {
                poly(dl, x, y, u, color, t, true, 4, 9.5f, 8.5f, 9.5f, 18, 4.5f, 18, 19.5f, 8.5f, 14.5f, 4, 14.5f);
                poly(dl, x, y, u, color, t, false, 8.5f, 14.5f, 10, 20, 12.5f, 20, 11.8f, 15.6f);
                line(dl, x, y, u, color, t, 21, 10, 21, 14);
            }
            case CLOCK -> {
                dl.addCircle(x + 12 * u, y + 12 * u, 9 * u, color, 0, t);
                poly(dl, x, y, u, color, t, false, 12, 7, 12, 12, 15.5f, 14);
            }
            case IMAGE -> {
                rect(dl, x, y, u, color, t, 3, 4.5f, 21, 19.5f, 2.5f);
                dl.addCircle(x + 8.5f * u, y + 9.5f * u, 1.8f * u, color, 0, t);
                poly(dl, x, y, u, color, t, false, 3.5f, 17, 9, 12, 13, 16, 16, 13, 20.5f, 17.5f);
            }
            case LOGOUT -> {
                poly(dl, x, y, u, color, t, false, 10, 4, 4, 4, 4, 20, 10, 20);
                line(dl, x, y, u, color, t, 9, 12, 20, 12);
                poly(dl, x, y, u, color, t, false, 16, 8, 20, 12, 16, 16);
            }
            case EXTERNAL -> {
                poly(dl, x, y, u, color, t, false, 14, 4, 20, 4, 20, 10);
                line(dl, x, y, u, color, t, 20, 4, 11, 13);
                poly(dl, x, y, u, color, t, false, 17, 14, 17, 20, 4, 20, 4, 7, 10, 7);
            }
            case ALERT -> {
                poly(dl, x, y, u, color, t, true, 12, 3.5f, 21.5f, 20, 2.5f, 20);
                line(dl, x, y, u, color, t, 12, 9.5f, 12, 14);
                dl.addCircleFilled(x + 12 * u, y + 17 * u, 1.1f * u, color);
            }
            case COPY -> {
                rect(dl, x, y, u, color, t, 8.5f, 8.5f, 20, 20, 2);
                poly(dl, x, y, u, color, t, false, 15.5f, 8.5f, 15.5f, 4, 4, 4, 4, 15.5f, 8.5f, 15.5f);
            }
            case CHIP -> {
                rect(dl, x, y, u, color, t, 6.5f, 6.5f, 17.5f, 17.5f, 2);
                rect(dl, x, y, u, color, t, 10, 10, 14, 14, 0.5f);
                for (float p : new float[]{9.5f, 14.5f}) {
                    line(dl, x, y, u, color, t, p, 3, p, 6.5f);
                    line(dl, x, y, u, color, t, p, 17.5f, p, 21);
                    line(dl, x, y, u, color, t, 3, p, 6.5f, p);
                    line(dl, x, y, u, color, t, 17.5f, p, 21, p);
                }
            }
            case COFFEE -> {
                poly(dl, x, y, u, color, t, false, 4.5f, 9, 4.5f, 16, 7, 20, 14, 20, 16.5f, 16, 16.5f, 9, 4.5f, 9);
                dl.pathClear();
                dl.pathArcTo(x + 17 * u, y + 12.5f * u, 3 * u, -PI * 0.5f, PI * 0.5f, 10);
                dl.pathStroke(color, 0, t);
                line(dl, x, y, u, color, t, 8.5f, 3.5f, 8.5f, 6);
                line(dl, x, y, u, color, t, 12.5f, 3.5f, 12.5f, 6);
            }
            case WRENCH -> {
                line(dl, x, y, u, color, t * 1.35f, 5, 19, 12.5f, 11.5f);
                dl.pathClear();
                dl.pathArcTo(x + 15.5f * u, y + 8.5f * u, 4.6f * u, PI * 0.95f, PI * 2.55f, 20);
                dl.pathStroke(color, 0, t);
            }
            case CUBE -> {
                poly(dl, x, y, u, color, t, true, 12, 3, 20.5f, 7.5f, 20.5f, 16.5f, 12, 21, 3.5f, 16.5f, 3.5f, 7.5f);
                poly(dl, x, y, u, color, t, false, 3.5f, 7.5f, 12, 12, 20.5f, 7.5f);
                line(dl, x, y, u, color, t, 12, 12, 12, 21);
            }
            case DISCORD_WAVE -> {
                rect(dl, x, y, u, color, t, 3, 6, 21, 18, 5);
                dl.addCircleFilled(x + 9 * u, y + 12 * u, 1.6f * u, color);
                dl.addCircleFilled(x + 15 * u, y + 12 * u, 1.6f * u, color);
            }
            case SWAP -> {
                poly(dl, x, y, u, color, t, false, 4, 8.5f, 18.5f, 8.5f);
                poly(dl, x, y, u, color, t, false, 15, 5, 18.5f, 8.5f, 15, 12);
                poly(dl, x, y, u, color, t, false, 20, 15.5f, 5.5f, 15.5f);
                poly(dl, x, y, u, color, t, false, 9, 12, 5.5f, 15.5f, 9, 19);
            }
            case CROWN -> {
                poly(dl, x, y, u, color, t, true, 3.5f, 17.5f, 3, 7.5f, 8.2f, 11.8f, 12, 4.5f, 15.8f, 11.8f, 21, 7.5f, 20.5f, 17.5f);
                line(dl, x, y, u, color, t, 4.5f, 20.5f, 19.5f, 20.5f);
                dl.addCircleFilled(x + 12 * u, y + 13.8f * u, 1.5f * u, color);
            }
            case HEART -> {
                float[] points = new float[64];
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI * 2 / 32;
                    double sin = Math.sin(a);
                    double hx = 16 * sin * sin * sin;
                    double hy = 13 * Math.cos(a) - 5 * Math.cos(2 * a) - 2 * Math.cos(3 * a) - Math.cos(4 * a);
                    points[i * 2] = (float)(12 + hx * 0.52);
                    points[i * 2 + 1] = (float)(11.2 - hy * 0.52);
                }
                poly(dl, x, y, u, color, t, true, points);
            }
        }
    }

    private static void line(final ImDrawList dl, final float x, final float y, final float u, final int color, final float t,
                             final float x0, final float y0, final float x1, final float y1) {
        dl.addLine(x + x0 * u, y + y0 * u, x + x1 * u, y + y1 * u, color, t);
    }

    private static void rect(final ImDrawList dl, final float x, final float y, final float u, final int color, final float t,
                             final float x0, final float y0, final float x1, final float y1, final float rounding) {
        dl.addRect(x + x0 * u, y + y0 * u, x + x1 * u, y + y1 * u, color, rounding * u, ImDrawFlags.None, t);
    }

    private static void poly(final ImDrawList dl, final float x, final float y, final float u, final int color, final float t,
                             final boolean closed, final float... points) {
        dl.pathClear();
        for (int i = 0; i + 1 < points.length; i += 2) {
            dl.pathLineTo(x + points[i] * u, y + points[i + 1] * u);
        }
        dl.pathStroke(color, closed ? ImDrawFlags.Closed : ImDrawFlags.None, t);
    }

    private static void star(final ImDrawList dl, final float cx, final float cy, final float r, final float w, final int color) {
        dl.pathClear();
        dl.pathLineTo(cx, cy - r);
        dl.pathLineTo(cx + w, cy);
        dl.pathLineTo(cx, cy + r);
        dl.pathLineTo(cx - w, cy);
        dl.pathFillConvex(color);
        dl.pathClear();
        dl.pathLineTo(cx - r, cy);
        dl.pathLineTo(cx, cy - w);
        dl.pathLineTo(cx + r, cy);
        dl.pathLineTo(cx, cy + w);
        dl.pathFillConvex(color);
    }
}
