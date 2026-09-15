package org.teamzetaverse.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.util.Json;

public final class AccountStore {
    private final LauncherPaths paths;
    private final List<Account> accounts = new CopyOnWriteArrayList<>();
    private final SecretStore secrets;
    private final SecretStore fallback;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ABNW account storage");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String lastStorageProblem;

    public AccountStore(final LauncherPaths paths) {
        this.paths = paths;
        this.secrets = SecretStore.platform(paths.root().resolve("accounts.protected"));
        this.fallback = SecretStore.ownerOnlyFile(paths.root().resolve("accounts.secret"));
        Map<String, JsonObject> tokens = new HashMap<>();
        boolean migrate = false;
        List<Account> loaded = new ArrayList<>();
        if (Files.isRegularFile(paths.accounts())) {
            try {
                JsonElement root = JsonParser.parseString(Files.readString(paths.accounts(), StandardCharsets.UTF_8));
                if (root.isJsonArray()) {
                    for (JsonElement element : root.getAsJsonArray()) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject object = element.getAsJsonObject();
                        Account account = Json.GSON.fromJson(object, Account.class);
                        if (account == null || account.uuid == null || account.uuid.isEmpty()) {
                            continue;
                        }
                        if (object.has("msaRefreshToken")) {
                            tokens.put(account.uuid, object);
                            migrate = true;
                        }
                        loaded.add(account);
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("accounts.json is unreadable: " + e.getMessage());
            }
        }
        JsonObject stored = this.loadSecrets();
        for (Account account : loaded) {
            JsonObject secret = stored == null ? null : Json.object(Json.object(stored, "accounts"), account.uuid);
            if (secret == null) {
                secret = tokens.get(account.uuid);
            }
            if (secret != null) {
                account.msaRefreshToken = valueOr(Json.string(secret, "msaRefreshToken"));
                account.minecraftToken = valueOr(Json.string(secret, "minecraftToken"));
                account.minecraftTokenExpiry = Json.number(secret, "minecraftTokenExpiry", 0L);
                account.cosmeticsToken = valueOr(Json.string(secret, "cosmeticsToken"));
            }
            if (!account.msaRefreshToken.isEmpty()) {
                this.accounts.add(account);
            }
        }
        if (migrate) {
            this.save();
        }
    }

    private static String valueOr(final String value) {
        return value == null ? "" : value;
    }

    private JsonObject loadSecrets() {
        for (SecretStore store : List.of(this.secrets, this.fallback)) {
            try {
                String text = store.load();
                if (text != null && !text.isBlank()) {
                    return Json.parseObject(text);
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not read saved sign-ins from " + store.describe() + ": " + e.getMessage());
                this.lastStorageProblem = e.getMessage();
            }
        }
        return null;
    }

    public Optional<String> storageProblem() {
        return Optional.ofNullable(this.lastStorageProblem);
    }

    public List<Account> all() {
        return this.accounts;
    }

    public Optional<Account> find(final String uuid) {
        return this.accounts.stream().filter(a -> a.uuid.equals(uuid)).findFirst();
    }

    public void put(final Account account) {
        this.accounts.removeIf(a -> a.uuid.equals(account.uuid));
        this.accounts.add(account);
        this.save();
    }

    public void remove(final Account account) {
        this.accounts.removeIf(a -> a.uuid.equals(account.uuid));
        this.save();
    }

    public void save() {
        List<Account> snapshot = new ArrayList<>(this.accounts);
        JsonObject all = new JsonObject();
        JsonObject perAccount = new JsonObject();
        for (Account account : snapshot) {
            JsonObject secret = new JsonObject();
            secret.addProperty("msaRefreshToken", account.msaRefreshToken);
            secret.addProperty("minecraftToken", account.minecraftToken);
            secret.addProperty("minecraftTokenExpiry", account.minecraftTokenExpiry);
            secret.addProperty("cosmeticsToken", account.cosmeticsToken == null ? "" : account.cosmeticsToken);
            perAccount.add(account.uuid, secret);
        }
        all.addProperty("formatVersion", 1);
        all.add("accounts", perAccount);
        String secretText = all.toString();
        JsonArray publicAccounts = Json.GSON.toJsonTree(snapshot).getAsJsonArray();
        this.writer.execute(() -> this.write(publicAccounts, secretText));
    }

    private synchronized void write(final JsonArray publicAccounts, final String secretText) {
        try {
            this.secrets.save(secretText);
            Files.deleteIfExists(this.paths.root().resolve("accounts.secret"));
            this.lastStorageProblem = null;
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not use " + this.secrets.describe() + " for sign-ins (" + e.getMessage() + "); falling back to "
                + this.fallback.describe() + ".");
            try {
                this.fallback.save(secretText);
                this.lastStorageProblem = this.secrets.describe() + " is unavailable; sign-ins are kept in " + this.fallback.describe() + ".";
            } catch (IOException | RuntimeException fallbackFailure) {
                System.err.println("Could not save sign-ins: " + fallbackFailure.getMessage());
                this.lastStorageProblem = "Sign-ins could not be saved: " + fallbackFailure.getMessage();
                return;
            }
        }
        try {
            Json.writeString(this.paths.accounts(), Json.GSON.toJson(publicAccounts));
        } catch (IOException e) {
            System.err.println("Could not save accounts.json: " + e.getMessage());
        }
    }

    public void flush() {
        this.writer.shutdown();
        try {
            this.writer.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
