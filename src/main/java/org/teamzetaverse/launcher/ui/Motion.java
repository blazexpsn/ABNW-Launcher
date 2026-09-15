package org.teamzetaverse.launcher.ui;

import imgui.ImGui;
import java.util.HashMap;
import java.util.Map;

final class Motion {
    private static final Map<String, float[]> VALUES = new HashMap<>();
    private static boolean moving;
    private static boolean movedLastFrame;

    private Motion() {
    }

    static void beginFrame() {
        movedLastFrame = moving;
        moving = false;
    }

    static boolean isMoving() {
        return movedLastFrame || moving;
    }

    static void keepAlive() {
        moving = true;
    }

    static float to(final String key, final float target, final float speed) {
        float[] value = VALUES.computeIfAbsent(key, k -> new float[]{target});
        float dt = Math.min(0.1f, Math.max(0f, ImGui.getIO().getDeltaTime()));
        value[0] += (target - value[0]) * (1f - (float)Math.exp(-speed * dt));
        if (Math.abs(target - value[0]) < 0.002f) {
            value[0] = target;
        } else {
            moving = true;
        }
        return value[0];
    }

    static float hover(final String key, final boolean hovered) {
        return to(key, hovered ? 1f : 0f, 16f);
    }

    static void set(final String key, final float value) {
        VALUES.computeIfAbsent(key, k -> new float[1])[0] = value;
        moving = true;
    }

    static float easeOutCubic(final float t) {
        float k = Math.max(0f, Math.min(1f, t));
        float inv = 1f - k;
        return 1f - inv * inv * inv;
    }
}
