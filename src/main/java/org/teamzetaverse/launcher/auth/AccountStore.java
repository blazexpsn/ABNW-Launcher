package org.teamzetaverse.launcher.auth;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.util.Json;

public final class AccountStore {
    private final LauncherPaths paths;
    private final List<Account> accounts = new CopyOnWriteArrayList<>();

    public AccountStore(final LauncherPaths paths) {
        this.paths = paths;
        if (Files.isRegularFile(paths.accounts())) {
            try {
                List<Account> loaded = Json.GSON.fromJson(Files.readString(paths.accounts(), StandardCharsets.UTF_8),
                    new TypeToken<ArrayList<Account>>() {}.getType());
                if (loaded != null) {
                    loaded.stream().filter(a -> a != null && !a.uuid.isEmpty() && !a.msaRefreshToken.isEmpty()).forEach(this.accounts::add);
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("accounts.json is unreadable: " + e.getMessage());
            }
        }
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

    public synchronized void save() {
        try {
            Json.write(this.paths.accounts(), new ArrayList<>(this.accounts));
        } catch (IOException e) {
            System.err.println("Could not save accounts.json: " + e.getMessage());
        }
    }
}
