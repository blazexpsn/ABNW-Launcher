package org.teamzetaverse.launcher.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.auth.Account;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.util.Json;

public final class CosmeticsClient {
    private static final String MOJANG_JOIN = "https://sessionserver.mojang.com/session/minecraft/join";

    public static final class UnauthorizedException extends IOException {
        UnauthorizedException(final String message) {
            super(message);
        }
    }

    public record Patreon(boolean linked, boolean active, String fullName, String status) {
    }

    public record Status(String uuid, Patreon patreon, boolean cosmeticsAvailable, List<String> owned, List<String> unlocked) {
    }

    public record PatreonLink(String url, long expiresAtMillis) {
    }

    public String signIn(final Account account) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("uuid", account.uuid);
        request.addProperty("name", account.name);
        JsonObject challenge = this.call("POST", "/v1/auth/challenge", request, null);
        String challengeId = Json.string(challenge, "challengeId");
        String serverId = Json.string(challenge, "serverId");
        if (challengeId == null || serverId == null) {
            throw new IOException("The cosmetics service sent an incomplete sign-in challenge.");
        }

        JsonObject join = new JsonObject();
        join.addProperty("accessToken", account.minecraftToken);
        join.addProperty("selectedProfile", account.uuid.replace("-", ""));
        join.addProperty("serverId", serverId);
        Http.Response joined = Http.exchange("POST", MOJANG_JOIN, join.toString(), Map.of());
        if (!joined.ok()) {
            if (joined.status() == 403) {
                throw new IOException("Minecraft would not confirm " + account.name + ". Multiplayer may be turned off for this account in its Xbox privacy settings.");
            }
            throw new IOException("Minecraft could not confirm " + account.name + " (HTTP " + joined.status() + ").");
        }

        JsonObject verify = new JsonObject();
        verify.addProperty("challengeId", challengeId);
        JsonObject session = this.call("POST", "/v1/auth/verify", verify, null);
        String token = Json.string(session, "token");
        if (token == null || token.isBlank()) {
            throw new IOException("The cosmetics service did not issue a sign-in.");
        }
        return token;
    }

    public Status status(final String token) throws IOException {
        JsonObject body = this.call("GET", "/v1/me", null, token);
        JsonObject patreon = Json.object(body, "patreon");
        JsonObject cosmetics = Json.object(body, "cosmetics");
        List<String> owned = new ArrayList<>();
        JsonArray ownedArray = cosmetics != null && cosmetics.has("owned") && cosmetics.get("owned").isJsonArray() ? cosmetics.getAsJsonArray("owned") : new JsonArray();
        for (JsonElement element : ownedArray) {
            if (element.isJsonObject() && Json.string(element.getAsJsonObject(), "id") != null) {
                owned.add(Json.string(element.getAsJsonObject(), "id"));
            }
        }
        List<String> unlocked = new ArrayList<>();
        JsonArray unlockedArray = cosmetics != null && cosmetics.has("unlocked") && cosmetics.get("unlocked").isJsonArray() ? cosmetics.getAsJsonArray("unlocked") : new JsonArray();
        for (JsonElement element : unlockedArray) {
            if (element.isJsonPrimitive()) {
                unlocked.add(element.getAsString());
            }
        }
        return new Status(
            Json.string(body, "uuid"),
            new Patreon(flag(patreon, "linked"), flag(patreon, "active"), valueOr(Json.string(patreon, "fullName")), valueOr(Json.string(patreon, "status"))),
            flag(cosmetics, "available"),
            List.copyOf(owned),
            List.copyOf(unlocked));
    }

    public PatreonLink startPatreonLink(final String token) throws IOException {
        JsonObject body = this.call("POST", "/v1/patreon/link", new JsonObject(), token);
        String url = Json.string(body, "url");
        if (url == null || !url.startsWith("https://www.patreon.com/")) {
            throw new IOException("The cosmetics service sent an unexpected Patreon link.");
        }
        return new PatreonLink(url, System.currentTimeMillis() + Json.number(body, "expiresIn", 600) * 1000L);
    }

    public String startCheckout(final String token, final String cosmeticId) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("cosmeticId", cosmeticId);
        JsonObject body = this.call("POST", "/v1/store/checkout", request, token);
        String url = Json.string(body, "url");
        if (url == null || !url.startsWith("https://checkout.stripe.com/")) {
            throw new IOException("The cosmetics service sent an unexpected checkout link.");
        }
        return url;
    }

    public void unlinkPatreon(final String token) throws IOException {
        this.call("DELETE", "/v1/patreon/link", null, token);
    }

    private JsonObject call(final String method, final String path, final JsonObject body, final String token) throws IOException {
        if (!BuildInfo.cosmeticsConfigured()) {
            throw new IOException("Cosmetics aren't available in this launcher build.");
        }
        Map<String, String> headers = token == null ? Map.of() : Map.of("Authorization", "Bearer " + token);
        Http.Response response = Http.exchange(method, BuildInfo.COSMETICS_API_URL + path, body == null ? null : body.toString(), headers);
        JsonObject parsed;
        try {
            parsed = response.body() == null || response.body().isBlank() ? new JsonObject() : Json.parseObject(response.body());
        } catch (IOException e) {
            parsed = new JsonObject();
        }
        if (response.status() == 401) {
            throw new UnauthorizedException(valueOr(Json.string(parsed, "message")));
        }
        if (!response.ok()) {
            String message = Json.string(parsed, "message");
            throw new IOException(message != null ? message : "The cosmetics service answered HTTP " + response.status() + ".");
        }
        return parsed;
    }

    private static boolean flag(final JsonObject object, final String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    private static String valueOr(final String value) {
        return value == null ? "" : value;
    }
}
