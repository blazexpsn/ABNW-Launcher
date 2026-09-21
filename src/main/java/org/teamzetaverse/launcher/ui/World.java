package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;

final class World {
    static final float HORIZON = 136f;

    private static final String SKY = "resource:/assets/ui/sky.png";
    private static final String MOON = "resource:/assets/ui/moon.png";
    private static final String HILLS_FAR = "resource:/assets/ui/hills_far.png";
    private static final String HILLS_NEAR = "resource:/assets/ui/hills_near.png";
    private static final String GRASS = "resource:/assets/ui/grass.png";
    private static final String SOIL = "resource:/assets/ui/soil.png";
    private static final String STONE = "resource:/assets/ui/stone.png";
    private static final String CAVE = "resource:/assets/ui/cave.png";
    private static final String STALACTITES = "resource:/assets/ui/stalactites.png";
    private static final String STALAGMITES = "resource:/assets/ui/stalagmites.png";
    private static final String VILLAGE = "resource:/assets/ui/village.png";
    private static final String VILLAGE_GLOW = "resource:/assets/ui/village_glow.png";
    private static final String DEEPSLATE = "resource:/assets/ui/deepslate.png";
    private static final String DEEP = "resource:/assets/ui/deep.png";
    private static final String PENGUIN = "resource:/assets/ui/penguin.png";
    private static final String[] CLOUDS = {"resource:/assets/ui/cloud0.png", "resource:/assets/ui/cloud1.png", "resource:/assets/ui/cloud2.png"};

    private static final float SOIL_TOP = HORIZON + 4f;
    private static final float CAVE_TOP = 300f;
    private static final float CAVE_BOTTOM = 500f;
    private static final float HALL_TOP = 520f;
    private static final float HALL_FLOOR = 690f;
    private static final float DEEPSLATE_TOP = 700f;
    private static final float DEEP_TOP = 900f;

    private static final float[][] CLOUD_LANES = {
        {24f, 34f, 1.6f, 0f}, {96f, 58f, 2.4f, 1f}, {190f, 28f, 1.2f, 2f}, {296f, 78f, 2.0f, 1f}, {150f, 92f, 3.0f, 2f}, {8f, 96f, 2.6f, 1f}
    };

    private static final int PENGUIN_W = 26;
    private static final int PENGUIN_H = 24;
    private static final int STAND = 0;
    private static final int BREATHE = 1;
    private static final int BLINK = 2;
    private static final int WAVE_LOW = 3;
    private static final int WAVE_HIGH = 4;

    private float camera;
    private float horizonY;
    private float top;
    private float bottom;

    static float scale() {
        return Pixel.unit() * 2f;
    }

    static float depthOf(final LauncherUi.Page page) {
        return switch (page) {
            case HOME -> 0f;
            case NEWS -> 150f;
            case INSTANCES -> 290f;
            case COSMETICS -> 510f;
            case SETTINGS -> 700f;
        };
    }

    float horizonY() {
        return this.horizonY;
    }

    float screenY(final float worldY) {
        return this.top + (worldY - this.camera) * scale();
    }

    void draw(final ImDrawList dl, final float x0, final float y0, final float x1, final float y1, final LauncherUi.Page page, final float scroll) {
        float s = scale();
        float depth = Motion.to("world#depth", depthOf(page), 3.2f);
        this.camera = depth + scroll / s;
        this.top = y0;
        this.bottom = y1;
        this.horizonY = this.screenY(HORIZON);
        double t = ImGui.getTime();

        dl.addRectFilled(x0, y0, x1, y1, u32(0x1A0B26));

        if (this.visible(0f, HORIZON)) {
            this.band(dl, SKY, 0f, HORIZON, x0, x1, 0f);
            float twinkle = 0.55f + 0.25f * (float)Math.sin(t * 0.9);
            Pixel.tile(dl, Pixel.STARS, x0, this.screenY(0f), x1, this.screenY(HORIZON * 0.72f), Pixel.unit(), u32(0xFFFFFF, twinkle));
            this.sprite(dl, MOON, x1 - 82f * s, this.screenY(34f), s, 0xFFFFFF, 1f);
            for (float[] lane : CLOUD_LANES) {
                ImageCache.Texture cloud = Pixel.texture(CLOUDS[(int)lane[3]]);
                if (cloud == null) {
                    continue;
                }
                float span = (x1 - x0) + cloud.width() * s;
                float cx = x0 + (float)(((lane[0] * s + t * lane[2] * s) % span + span) % span) - cloud.width() * s;
                this.sprite(dl, CLOUDS[(int)lane[3]], cx, this.screenY(lane[1]), s, 0xFFFFFF, 0.95f);
            }
            this.row(dl, HILLS_FAR, HORIZON + 2f, x0, x1, (float)(t * 0.6));
            this.row(dl, HILLS_NEAR, HORIZON + 4f, x0, x1, (float)(t * 1.4));
        }
        this.band(dl, SOIL, SOIL_TOP, CAVE_TOP - 10f, x0, x1, 0f);
        this.band(dl, STONE, CAVE_TOP - 10f, CAVE_TOP, x0, x1, 0f);
        this.band(dl, CAVE, CAVE_TOP, CAVE_BOTTOM, x0, x1, 0f);
        this.band(dl, STONE, CAVE_BOTTOM, HALL_TOP, x0, x1, 0f);
        this.band(dl, CAVE, HALL_TOP, HALL_FLOOR, x0, x1, 0f);
        this.band(dl, STONE, HALL_FLOOR, DEEPSLATE_TOP, x0, x1, 0f);
        this.band(dl, DEEPSLATE, DEEPSLATE_TOP, DEEP_TOP, x0, x1, 0f);
        this.band(dl, DEEP, DEEP_TOP, DEEP_TOP + 4000f, x0, x1, 0f);
        this.hang(dl, STALACTITES, CAVE_TOP, x0, x1);
        ImageCache.Texture mites = Pixel.texture(STALAGMITES);
        if (mites != null) {
            this.hang(dl, STALAGMITES, CAVE_BOTTOM - mites.height(), x0, x1);
        }
        this.hang(dl, STALACTITES, HALL_TOP, x0, x1);
        this.hall(dl, x0, x1, t);
        this.band(dl, GRASS, HORIZON, HORIZON + 6f, x0, x1, 0f);

        if (this.visible(DEEP_TOP - 40f, DEEP_TOP + 200f)) {
            float glow = 0.12f + 0.06f * (float)Math.sin(t * 1.7);
            dl.addRectFilledMultiColor(x0, this.screenY(DEEP_TOP - 30f), x1, this.screenY(DEEP_TOP), u32(0xFF7828, 0f), u32(0xFF7828, 0f),
                u32(0xFF7828, glow), u32(0xFF7828, glow));
        }
    }

    private void hall(final ImDrawList dl, final float x0, final float x1, final double t) {
        ImageCache.Texture village = Pixel.texture(VILLAGE);
        if (village == null || !this.visible(HALL_TOP, HALL_FLOOR)) {
            return;
        }
        float villageTop = HALL_FLOOR - village.height();
        float warmth = 0.10f + 0.03f * (float)Math.sin(t * 0.8);
        dl.addRectFilledMultiColor(x0, this.screenY(villageTop - 40f), x1, this.screenY(HALL_FLOOR), u32(0xFF9A3C, 0f), u32(0xFF9A3C, 0f),
            u32(0xFF9A3C, warmth), u32(0xFF9A3C, warmth));
        this.band(dl, VILLAGE, villageTop, HALL_FLOOR, x0, x1, 0f, 0x7A6A94, 1f);
        float flicker = 0.8f + 0.12f * (float)Math.sin(t * 5.3) + 0.08f * (float)Math.sin(t * 13.1 + 1.7);
        this.band(dl, VILLAGE_GLOW, villageTop, HALL_FLOOR, x0, x1, 0f, 0xFFFFFF, flicker);
    }

    boolean isAmbient() {
        return true;
    }

    void penguin(final ImDrawList dl, final float x, final float feetY, final boolean excited) {
        ImageCache.Texture sheet = Pixel.texture(PENGUIN);
        if (sheet == null) {
            return;
        }
        float s = scale();
        double t = ImGui.getTime();
        int frame;
        if (excited) {
            frame = ((int)(t / 0.18)) % 2 == 0 ? WAVE_LOW : WAVE_HIGH;
        } else if (t % 4.3 < 0.16) {
            frame = BLINK;
        } else {
            frame = ((int)(t / 1.1)) % 2 == 0 ? STAND : BREATHE;
        }
        float w = PENGUIN_W * s;
        float h = PENGUIN_H * s;
        float px0 = Math.round(x);
        float py0 = Math.round(feetY - h);
        float u0 = (float)(frame * PENGUIN_W) / sheet.width();
        float u1 = (float)((frame + 1) * PENGUIN_W) / sheet.width();
        dl.addRectFilled(px0 + 5 * s, feetY - s, px0 + w - 5 * s, feetY + s, u32(0x10041A, 0.35f));
        dl.addImage(sheet.id(), px0, py0, px0 + w, py0 + h, u0, 0f, u1, 1f, u32(0xFFFFFF));
    }

    static void penguinIcon(final ImDrawList dl, final float x, final float y, final float box) {
        ImageCache.Texture sheet = Pixel.texture(PENGUIN);
        if (sheet == null) {
            return;
        }
        float s = Math.max(1f, (float)Math.floor(box / PENGUIN_W));
        float w = PENGUIN_W * s;
        float h = PENGUIN_H * s;
        float px0 = Math.round(x + (box - w) * 0.5f);
        float py0 = Math.round(y + (box - h) * 0.5f);
        dl.addImage(sheet.id(), px0, py0, px0 + w, py0 + h, 0f, 0f, (float)PENGUIN_W / sheet.width(), 1f, u32(0xFFFFFF));
    }

    static String penguinSprite(final Icons.Icon icon) {
        String pose = switch (icon) {
            case UPDATE, PENGUIN_ALERT -> "alert";
            case SOON, PENGUIN_CLOCK -> "clock";
            case PENGUIN_OK -> "ok";
            case PENGUIN_WARN -> "warn";
            case PENGUIN_NEWS -> "news";
            case PENGUIN_EMPTY -> "empty";
            case PENGUIN_BOX -> "box";
            case PENGUIN_PLAY -> "play";
            case PENGUIN_KEY -> "key";
            case PENGUIN_SUPPORTER -> "heart";
            default -> null;
        };
        return pose == null ? null : "resource:/assets/ui/penguin_" + pose + ".png";
    }

    static void penguinHolding(final ImDrawList dl, final String key, final float x, final float y, final float box) {
        ImageCache.Texture sprite = Pixel.texture(key);
        if (sprite == null) {
            return;
        }
        float s = Math.max(1f, Math.round(box * 1.2f / sprite.height()));
        float w = sprite.width() * s;
        float h = sprite.height() * s;
        float bob = (ImGui.getTime() % 1.2) < 0.6 ? 0f : s;
        float px0 = Math.round(x + (box - w) * 0.5f);
        float py0 = Math.round(y + box - h - bob);
        dl.addImage(sprite.id(), px0, py0, px0 + w, py0 + h, 0f, 0f, 1f, 1f, u32(0xFFFFFF));
    }

    static float penguinWidth() {
        return PENGUIN_W * scale();
    }

    private boolean visible(final float worldTop, final float worldBottom) {
        return this.screenY(worldBottom) > this.top && this.screenY(worldTop) < this.bottom;
    }

    private void sprite(final ImDrawList dl, final String key, final float x, final float y, final float s, final int rgb, final float alpha) {
        ImageCache.Texture texture = Pixel.texture(key);
        if (texture == null) {
            return;
        }
        float sx = Math.round(x);
        float sy = Math.round(y);
        dl.addImage(texture.id(), sx, sy, sx + texture.width() * s, sy + texture.height() * s, 0f, 0f, 1f, 1f, u32(rgb, alpha));
    }

    private void band(final ImDrawList dl, final String key, final float worldTop, final float worldBottom, final float x0, final float x1,
                      final float offset) {
        this.band(dl, key, worldTop, worldBottom, x0, x1, offset, 0xFFFFFF, 1f);
    }

    private void band(final ImDrawList dl, final String key, final float worldTop, final float worldBottom, final float x0, final float x1,
                      final float offset, final int rgb, final float alpha) {
        if (!this.visible(worldTop, worldBottom)) {
            return;
        }
        ImageCache.Texture texture = Pixel.texture(key);
        if (texture == null) {
            return;
        }
        float s = scale();
        float tw = texture.width() * s;
        float th = texture.height() * s;
        float yTop = this.screenY(worldTop);
        float yBottom = this.screenY(worldBottom);
        float clipTop = Math.max(yTop, this.top);
        float clipBottom = Math.min(yBottom, this.bottom);
        dl.pushClipRect(x0, clipTop, x1, clipBottom, true);
        float startY = yTop + (float)Math.floor((clipTop - yTop) / th) * th;
        float shift = ((offset * s) % tw + tw) % tw;
        for (float ty = startY; ty < clipBottom; ty += th) {
            for (float tx = x0 - shift; tx < x1; tx += tw) {
                dl.addImage(texture.id(), Math.round(tx), Math.round(ty), Math.round(tx) + tw, Math.round(ty) + th, 0f, 0f, 1f, 1f, u32(rgb, alpha));
            }
        }
        dl.popClipRect();
    }

    private void row(final ImDrawList dl, final String key, final float groundY, final float x0, final float x1, final float offset) {
        ImageCache.Texture texture = Pixel.texture(key);
        if (texture == null) {
            return;
        }
        this.band(dl, key, groundY - texture.height(), groundY, x0, x1, offset);
    }

    private void hang(final ImDrawList dl, final String key, final float worldTop, final float x0, final float x1) {
        ImageCache.Texture texture = Pixel.texture(key);
        if (texture == null) {
            return;
        }
        this.band(dl, key, worldTop, worldTop + texture.height(), x0, x1, 0f);
    }
}
