package org.teamzetaverse.launcher.server;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Json;

/** Resolves Java Edition usernames through Mojang, never an offline-name UUID. */
public final class PlayerProfiles {
    public record Profile(UUID uuid, String name) {}

    private PlayerProfiles() {}

    public static Profile lookup(final String username) throws IOException {
        String name = username.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IOException("Enter a Minecraft Java username (3–16 letters, numbers or underscores).");
        }
        Http.Response response = Http.exchange("GET", "https://api.mojang.com/users/profiles/minecraft/" + name, null, Map.of());
        if (response.status() == 204 || response.status() == 404) {
            throw new IOException("No Minecraft Java profile was found for " + name + ".");
        }
        if (response.status() == 429) {
            throw new IOException("Minecraft profile lookup is busy. Try again shortly.");
        }
        if (!response.ok()) {
            throw new IOException("Minecraft profile lookup failed (HTTP " + response.status() + "). Try again later.");
        }
        return parse(response.body());
    }

    public static Profile parse(final String body) throws IOException {
        JsonObject json = Json.parseObject(body);
        String id = Json.string(json, "id");
        String name = Json.string(json, "name");
        if (id == null || !id.matches("[a-fA-F0-9]{32}") || name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IOException("Minecraft returned an invalid player profile.");
        }
        String dashed = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-"
            + id.substring(16, 20) + "-" + id.substring(20);
        return new Profile(UUID.fromString(dashed), name);
    }
}
