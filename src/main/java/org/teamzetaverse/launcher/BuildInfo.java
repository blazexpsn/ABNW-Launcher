package org.teamzetaverse.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String VERSION;
    public static final String REPOSITORY;
    public static final String BRANCH;
    public static final String LAUNCHER_REPOSITORY;
    public static final String LAUNCHER_BRANCH;
    public static final String MSA_CLIENT_ID;
    public static final String DISCORD_CLIENT_ID;

    static {
        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/abnw-launcher.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
        }
        VERSION = clean(properties.getProperty("version"), "dev");
        REPOSITORY = clean(properties.getProperty("repository"), "blazexpsn/Minecraft-ABNW");
        BRANCH = clean(properties.getProperty("branch"), "main");
        LAUNCHER_REPOSITORY = clean(properties.getProperty("launcherRepository"), "blazexpsn/ABNW-Launcher");
        LAUNCHER_BRANCH = clean(properties.getProperty("launcherBranch"), "main");
        MSA_CLIENT_ID = clean(properties.getProperty("msaClientId"), "");
        DISCORD_CLIENT_ID = clean(properties.getProperty("discordClientId"), "");
    }

    private BuildInfo() {
    }

    private static String clean(final String value, final String fallback) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }

    public static boolean isDevBuild() {
        return "dev".equals(VERSION);
    }

    public static String versionsManifestUrl() {
        return "https://raw.githubusercontent.com/" + REPOSITORY + "/" + BRANCH + "/abnw-versions.json";
    }

    public static String releaseDownloadsPrefix() {
        return "https://github.com/" + REPOSITORY + "/releases/download/";
    }

    public static String feedApiUrl(final String fileName) {
        return "https://api.github.com/repos/" + LAUNCHER_REPOSITORY + "/contents/feed/" + fileName + "?ref=" + LAUNCHER_BRANCH;
    }

    public static String feedRawUrl(final String fileName) {
        return "https://raw.githubusercontent.com/" + LAUNCHER_REPOSITORY + "/" + LAUNCHER_BRANCH + "/feed/" + fileName;
    }

    public static String latestLauncherReleaseApi() {
        return "https://api.github.com/repos/" + LAUNCHER_REPOSITORY + "/releases/latest";
    }

    public static String launcherReleasesPage() {
        return "https://github.com/" + LAUNCHER_REPOSITORY + "/releases";
    }
}
