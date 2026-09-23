package org.teamzetaverse.launcher.auth;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import org.teamzetaverse.launcher.util.Json;

/** Launcher-private credentials. No plaintext fallback and no credentials in public cosmetic APIs. */
public final class CosmeticIdentityStore {
    public record Identity(String id, String label) {}
    private final SecretStore secrets;
    private JsonObject data = new JsonObject();
    private IOException loadFailure;

    public CosmeticIdentityStore(Path root) {
        this.secrets = SecretStore.platform(root.resolve("cosmetic-identities.protected"), "cosmetic-identities");
        try {
            String stored = this.secrets.load();
            if (stored != null && !stored.isBlank()) this.data = Json.parseObject(stored);
        } catch (IOException | RuntimeException e) { this.loadFailure = new IOException("Cannot unlock saved ABNW cosmetic identities."); }
    }

    public synchronized List<Identity> all() {
        List<Identity> result = new ArrayList<>();
        for (var entry : this.identities().entrySet()) {
            result.add(new Identity(entry.getKey(), Json.string(entry.getValue().getAsJsonObject(), "label")));
        }
        return List.copyOf(result);
    }
    public synchronized String selected() { return Optional.ofNullable(Json.string(this.data, "selected")).orElse(""); }
    public synchronized void select(String id) throws IOException {
        if (!id.isEmpty() && !this.identities().has(id)) throw new IOException("Unknown ABNW identity.");
        this.data.addProperty("selected", id); this.save();
    }
    public synchronized String forLogin(String uuid) {
        JsonObject logins = Json.object(this.data, "logins");
        return Optional.ofNullable(Json.string(logins, uuid)).orElse("");
    }
    public synchronized String prepare(String uuid) throws IOException {
        this.checkStorage();
        JsonObject pending = this.child("pending");
        String secret = Json.string(pending, uuid);
        if (secret == null) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            pending.addProperty(uuid, secret);
        }
        // Persist BEFORE registration, so a lost response can always be retried with the same secret.
        this.save();
        return secret;
    }
    public synchronized void accept(String uuid, String id, String label, String credential) throws IOException {
        if (!id.matches("abnw_[0-9a-f]{32}") || !credential.matches("[A-Za-z0-9_-]{43}")) throw new IOException("Invalid ABNW identity response.");
        JsonObject entry = Json.object(this.identities(), id);
        if (entry == null) entry = new JsonObject();
        entry.addProperty("label", label);
        entry.addProperty("credential", credential);
        this.identities().add(id, entry);
        this.child("logins").addProperty(uuid, id);
        this.child("pending").remove(uuid);
        this.data.addProperty("selected", id);
        this.save();
    }
    public synchronized String credential(String id) throws IOException {
        this.checkStorage();
        String secret = Json.string(Json.object(this.identities(), id), "credential");
        if (secret == null || secret.isBlank()) throw new IOException("Select a locally saved ABNW cosmetic identity.");
        return secret;
    }
    public synchronized long revision(String id) { return Json.number(Json.object(this.identities(), id), "revision", 0); }
    public synchronized void revision(String id, long revision) throws IOException {
        JsonObject entry = Json.object(this.identities(), id);
        if (entry == null) throw new IOException("Unknown ABNW identity.");
        if (revision > this.revision(id)) { entry.addProperty("revision", revision); this.save(); }
    }
    private JsonObject identities() { return this.child("identities"); }
    private JsonObject child(String key) {
        JsonObject child = Json.object(this.data, key);
        if (child == null) { child = new JsonObject(); this.data.add(key, child); }
        return child;
    }
    private void checkStorage() throws IOException { if (this.loadFailure != null) throw this.loadFailure; }
    private void save() throws IOException {
        this.checkStorage();
        try { this.secrets.save(this.data.toString()); }
        catch (IOException | RuntimeException e) { throw new IOException("ABNW credentials could not be saved to the OS credential store. Enable the system keyring and retry."); }
    }
}
