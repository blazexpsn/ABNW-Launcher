package org.teamzetaverse.launcher.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import org.teamzetaverse.launcher.util.Json;
import org.teamzetaverse.launcher.util.OperatingSystem;

public final class Rules {
    private Rules() {
    }

    public static boolean allowed(final JsonArray rules, final Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            if (matches(rule, features)) {
                allowed = "allow".equals(Json.string(rule, "action"));
            }
        }
        return allowed;
    }

    private static boolean matches(final JsonObject rule, final Map<String, Boolean> features) {
        JsonObject os = Json.object(rule, "os");
        if (os != null) {
            String name = Json.string(os, "name");
            if (name != null && !name.equals(OperatingSystem.CURRENT.mojangName)) {
                return false;
            }
            String arch = Json.string(os, "arch");
            if (arch != null && !arch.equals(OperatingSystem.mojangArch())) {
                return false;
            }
            String version = Json.string(os, "version");
            if (version != null && !System.getProperty("os.version", "").matches(version)) {
                return false;
            }
        }
        JsonObject required = Json.object(rule, "features");
        if (required != null) {
            for (Map.Entry<String, JsonElement> feature : required.entrySet()) {
                boolean have = features.getOrDefault(feature.getKey(), false);
                if (have != feature.getValue().getAsBoolean()) {
                    return false;
                }
            }
        }
        return true;
    }
}
