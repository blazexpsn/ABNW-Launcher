package org.teamzetaverse.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.teamzetaverse.launcher.util.Json;

public final class LauncherConfig {
    public String msaClientId = "";
    public String javaPath = "";
    public int defaultMemoryMb = 4096;
    public String defaultRenderer = "auto";
    public String selectedAccount = "";
    public String selectedInstance = "";
    public float uiScale = 1.0f;
    public boolean discordPresence = true;
    public String lastSeenGameRelease = "";
    public List<String> dismissedAnnouncements = new ArrayList<>();

    private transient LauncherPaths paths;

    public static LauncherConfig load(final LauncherPaths paths) {
        LauncherConfig config = null;
        if (Files.isRegularFile(paths.config())) {
            try {
                config = Json.read(paths.config(), LauncherConfig.class);
            } catch (IOException e) {
                System.err.println("launcher.json is unreadable, using defaults: " + e.getMessage());
            }
        }
        if (config == null) {
            config = new LauncherConfig();
        }
        if (config.dismissedAnnouncements == null) {
            config.dismissedAnnouncements = new ArrayList<>();
        }
        if (config.lastSeenGameRelease == null) {
            config.lastSeenGameRelease = "";
        }
        config.paths = paths;
        return config;
    }

    public void save() {
        try {
            Json.write(this.paths.config(), this);
        } catch (IOException e) {
            System.err.println("Could not save launcher.json: " + e.getMessage());
        }
    }

    /** ABNW's own client ID, or the one set in Settings. Used for device-code sign-in and as the fallback. */
    public String effectiveClientId() {
        return this.hasClientIdOverride() ? this.msaClientId.trim() : BuildInfo.MSA_CLIENT_ID;
    }

    /** The client ID sign-in tries first: a Settings override wins, then the built-in primary client ID, then ABNW's own. */
    public String primaryClientId() {
        if (this.hasClientIdOverride() || BuildInfo.MSA_PRIMARY_CLIENT_ID.isEmpty()) {
            return this.effectiveClientId();
        }
        return BuildInfo.MSA_PRIMARY_CLIENT_ID;
    }

    /** The client ID to fall back to if the primary one is refused, or null when there is nothing different to try. */
    public String fallbackClientId() {
        String fallback = this.effectiveClientId();
        return fallback.isEmpty() || fallback.equalsIgnoreCase(this.primaryClientId()) ? null : fallback;
    }

    private boolean hasClientIdOverride() {
        return this.msaClientId != null && !this.msaClientId.isBlank();
    }
}
