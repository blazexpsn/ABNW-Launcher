package org.teamzetaverse.launcher.instance;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teamzetaverse.launcher.release.Release;

public final class Instance {
    public static final String FILE = "instance.json";
    public static final String LIBRARIES_FILE = "libraries.json";
    public static final String GAME_FOLDER = "minecraft";
    public static final String ICON_PENGUIN = "penguin";
    public static final String ICON_IMAGE = "image";

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
    public String icon = ICON_PENGUIN;
    public String iconFile = "";
    public Map<String, String> penguin = new LinkedHashMap<>();

    private transient Path folder;

    public Path folder() {
        return this.folder;
    }

    void folder(final Path folder) {
        this.folder = folder;
    }

    public Path customIcon() {
        if (!ICON_IMAGE.equals(this.icon) || this.iconFile == null || !this.iconFile.matches("icon-[0-9]{1,19}\\.(png|jpg|jpeg)")) {
            return null;
        }
        return this.folder.resolve(this.iconFile);
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
