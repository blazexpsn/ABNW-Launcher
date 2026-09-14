package org.teamzetaverse.launcher.util;

import java.util.Locale;

public enum OperatingSystem {
    WINDOWS("windows", "windows"),
    MACOS("osx", "macos"),
    LINUX("linux", "linux");

    public final String mojangName;
    public final String lwjglName;

    OperatingSystem(final String mojangName, final String lwjglName) {
        this.mojangName = mojangName;
        this.lwjglName = lwjglName;
    }

    public static final OperatingSystem CURRENT = detect();

    private static OperatingSystem detect() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return MACOS;
        }
        return LINUX;
    }

    public static boolean isArm64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.equals("aarch64") || arch.equals("arm64");
    }

    public static boolean is32Bit() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.equals("x86") || arch.equals("i386") || arch.equals("i686");
    }

    public static String mojangArch() {
        return is32Bit() ? "x86" : isArm64() ? "arm64" : "x86_64";
    }

    public String javaRuntimePlatform() {
        return switch (this) {
            case WINDOWS -> isArm64() ? "windows-arm64" : is32Bit() ? "windows-x86" : "windows-x64";
            case MACOS -> isArm64() ? "mac-os-arm64" : "mac-os";
            case LINUX -> is32Bit() ? "linux-i386" : "linux";
        };
    }

    public String javaExecutableName() {
        return this == WINDOWS ? "javaw.exe" : "java";
    }
}
