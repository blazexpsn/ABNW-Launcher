package org.teamzetaverse.launcher.instance;

import java.nio.file.Path;
import org.teamzetaverse.launcher.release.Release;

public final class Instance {
    public static final String FILE = "instance.json";
    public static final String LIBRARIES_FILE = "libraries.json";
    public static final String GAME_FOLDER = "minecraft";

    public int formatVersion = 1;
    public String id = "";
    public String name = "";
    public Release release = new Release();
    public String renderer = "auto";
    public int memoryMb;
    public String extraJvmArgs = "";
    public long created;
    public long lastPlayed;
    public long totalPlayMillis;

    private transient Path folder;

    public Path folder() {
        return this.folder;
    }

    void folder(final Path folder) {
        this.folder = folder;
    }

    public Path gameFolder() {
        return this.folder.resolve(GAME_FOLDER);
    }

    public Path librariesFile() {
        return this.folder.resolve(LIBRARIES_FILE);
    }

    public Path modsFolder() {
        return this.gameFolder().resolve("mods");
    }

    public Path nativesFolder() {
        return this.folder.resolve("natives");
    }
}
