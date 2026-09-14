package org.teamzetaverse.launcher;

import java.nio.file.Path;
import org.teamzetaverse.launcher.util.OperatingSystem;

public final class LauncherPaths {
    private final Path root;

    public LauncherPaths(final Path root) {
        this.root = root.toAbsolutePath();
    }

    public static LauncherPaths defaultLocation() {
        String override = System.getProperty("abnw.launcher.dataDir");
        if (override != null && !override.isBlank()) {
            return new LauncherPaths(Path.of(override));
        }
        String home = System.getProperty("user.home");
        return new LauncherPaths(switch (OperatingSystem.CURRENT) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, "ABNWLauncher");
            }
            case MACOS -> Path.of(home, "Library", "Application Support", "ABNWLauncher");
            case LINUX -> {
                String data = System.getenv("XDG_DATA_HOME");
                yield data != null && !data.isBlank() ? Path.of(data, "abnwlauncher") : Path.of(home, ".local", "share", "abnwlauncher");
            }
        });
    }

    public Path root() {
        return this.root;
    }

    public Path config() {
        return this.root.resolve("launcher.json");
    }

    public Path accounts() {
        return this.root.resolve("accounts.json");
    }

    public Path instances() {
        return this.root.resolve("instances");
    }

    public Path versions() {
        return this.root.resolve("versions");
    }

    public Path libraries() {
        return this.root.resolve("libraries");
    }

    public Path assets() {
        return this.root.resolve("assets");
    }

    public Path runtimes() {
        return this.root.resolve("runtimes");
    }

    public Path deltas() {
        return this.root.resolve("abnw").resolve("deltas");
    }

    public Path patchedJars() {
        return this.root.resolve("abnw").resolve("jars");
    }

    public Path logs() {
        return this.root.resolve("logs");
    }
}
