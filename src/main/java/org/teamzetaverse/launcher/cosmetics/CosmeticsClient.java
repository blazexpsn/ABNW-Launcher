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
    private final org.teamzetaverse.launcher.auth.CosmeticIdentityStore identities;
    private final java.nio.file.Path root;
    private final Map<String, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, org.teamzetaverse.cosmetics.api.SignedEntitlement> manifests = new java.util.concurrent.ConcurrentHashMap<>();
    private record Session(String token, long expiresAt) {
        @Override public String toString() { return "ABNW session [redacted]"; }
    }

    public CosmeticsClient(org.teamzetaverse.launcher.LauncherPaths paths) {
        this.root = paths.root().resolve("cosmetics");
        this.identities = new org.teamzetaverse.launcher.auth.CosmeticIdentityStore(paths.root());
    }
    public org.teamzetaverse.launcher.auth.CosmeticIdentityStore identities() { return this.identities; }

    public synchronized void initialize(Account account) throws IOException {
        if (account.devOffline) return;
        String existing = this.identities.forLogin(account.uuid);
        if (!existing.isEmpty()) { this.identities.select(existing); return; }
        String credential = this.identities.prepare(account.uuid);
        JsonObject response;
        if (account.cosmeticsToken != null && !account.cosmeticsToken.isBlank()) {
            JsonObject body = new JsonObject(); body.addProperty("credential", credential);
            response = this.call("POST", "/v1/identity/migrate", body, account.cosmeticsToken);
        } else {
            response = this.call("POST", "/v1/identity/register", new JsonObject(), credential);
        }
        String id = Json.string(response, "accountId");
        this.identities.accept(account.uuid, id, account.name, credential);
        this.rememberSession(id, response);
        account.cosmeticsToken = "";
    }

    public String session(String id) throws IOException {
        Session existing = this.sessions.get(id);
        if (existing != null && existing.expiresAt() > System.currentTimeMillis() / 1000 + 30) return existing.token();
        JsonObject response = this.call("POST", "/v1/auth/session", new JsonObject(), this.identities.credential(id));
        this.rememberSession(id, response);
        return this.sessions.get(id).token();
    }
    private void rememberSession(String id, JsonObject response) throws IOException {
        String token = Json.string(response, "token");
        if (token == null || token.isBlank()) throw new IOException("ABNW did not issue a session.");
        this.sessions.put(id, new Session(token, Json.number(response, "expiresAt", 0)));
    }
    private String binding(String id) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(this.identities.credential(id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public java.nio.file.Path identityDirectory(String id) {
        if (!id.matches("abnw_[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid ABNW identity.");
        return this.root.resolve(id);
    }
    private org.teamzetaverse.cosmetics.api.SignedEntitlement verify(String id, byte[] bytes) throws IOException {
        return org.teamzetaverse.cosmetics.api.SignedEntitlement.verify(bytes,
            org.teamzetaverse.cosmetics.api.SignedEntitlement.bundledKeys(), id, this.binding(id), this.identities.revision(id));
    }
    public Status cachedStatus(String id) throws IOException {
        var manifest = this.manifests.get(id);
        if (manifest == null) {
            java.nio.file.Path file = this.identityDirectory(id).resolve("entitlement.abne");
            if (!java.nio.file.Files.isRegularFile(file)) return null;
            if (java.nio.file.Files.size(file) > 525000) throw new IOException("Cached entitlement is too large.");
            manifest = this.verify(id, java.nio.file.Files.readAllBytes(file));
            this.manifests.put(id, manifest);
        }
        List<String> owned = List.copyOf(manifest.ownedAt(System.currentTimeMillis() / 1000));
        return new Status(id, new Patreon(false, false, "", ""), true, owned, owned);
    }
    public java.util.Set<String> unlocked() {
        String id = this.identities.selected();
        if (id.isEmpty()) return java.util.Set.of();
        try { this.cachedStatus(id); } catch (IOException ignored) { return java.util.Set.of(); }
        var manifest = this.manifests.get(id);
        return manifest == null ? java.util.Set.of() : manifest.ownedAt(System.currentTimeMillis() / 1000);
    }

    public static final class UnauthorizedException extends IOException {
        UnauthorizedException(final String message) {
            super(message);
        }
    }

    public record Patreon(boolean linked, boolean active, String fullName, String status) {
    }

    public record Status(String accountId, Patreon patreon, boolean cosmeticsAvailable, List<String> owned, List<String> unlocked) {
    }

    public record PatreonLink(String url, long expiresAtMillis) {
    }

    public synchronized Status status(final String id) throws IOException {
        JsonObject body;
        try { body = this.call("GET", "/v1/entitlements", null, this.session(id)); }
        catch (UnauthorizedException expired) {
            this.sessions.remove(id);
            body = this.call("GET", "/v1/entitlements", null, this.session(id));
        }
        JsonObject envelope = Json.object(body, "manifest");
        byte[] bytes;
        try {
            bytes = org.teamzetaverse.cosmetics.api.SignedEntitlement.envelope(Json.string(envelope, "keyId"),
                java.util.Base64.getDecoder().decode(Json.string(envelope, "payload")),
                java.util.Base64.getDecoder().decode(Json.string(envelope, "signature")));
        } catch (RuntimeException e) { throw new IOException("ABNW sent an invalid entitlement."); }
        var manifest = this.verify(id, bytes);
        java.nio.file.Path file = this.identityDirectory(id).resolve("entitlement.abne");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Path temp = java.nio.file.Files.createTempFile(file.getParent(), "entitlement-", ".part");
        try {
            java.nio.file.Files.write(temp, bytes);
            org.teamzetaverse.launcher.util.FileMoves.replace(temp, file);
        } finally { java.nio.file.Files.deleteIfExists(temp); }
        this.identities.revision(id, manifest.revision());
        this.manifests.put(id, manifest);
        JsonObject patreon = Json.object(body, "patreon");
        List<String> owned = List.copyOf(manifest.ownedAt(System.currentTimeMillis() / 1000));
        return new Status(id, new Patreon(flag(patreon, "linked"), flag(patreon, "active"), valueOr(Json.string(patreon, "fullName")), valueOr(Json.string(patreon, "status"))),
            flag(body, "cosmeticsAvailable"), owned, owned);
    }

    public PatreonLink startPatreonLink(final String token) throws IOException {
        JsonObject body = this.call("POST", "/v1/patreon/link", new JsonObject(), this.session(token));
        String url = Json.string(body, "url");
        if (url == null || !url.startsWith("https://www.patreon.com/")) {
            throw new IOException("The cosmetics service sent an unexpected Patreon link.");
        }
        return new PatreonLink(url, System.currentTimeMillis() + Json.number(body, "expiresIn", 600) * 1000L);
    }

    public String startCheckout(final String token, final String cosmeticId) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("cosmeticId", cosmeticId);
        JsonObject body = this.call("POST", "/v1/store/checkout", request, this.session(token));
        String url = Json.string(body, "url");
        if (url == null || !url.startsWith("https://checkout.stripe.com/")) {
            throw new IOException("The cosmetics service sent an unexpected checkout link.");
        }
        return url;
    }

    public void unlinkPatreon(final String token) throws IOException {
        this.call("DELETE", "/v1/patreon/link", null, this.session(token));
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
