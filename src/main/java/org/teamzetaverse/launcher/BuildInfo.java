package org.teamzetaverse.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String VERSION;
    public static final String REPOSITORY;
    public static final String BRANCH;
    public static final String MSA_CLIENT_ID;

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
        MSA_CLIENT_ID = clean(properties.getProperty("msaClientId"), "");
    }

    private BuildInfo() {
    }

    private static String clean(final String value, final String fallback) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }

    public static String versionsManifestUrl() {
        return "https://raw.githubusercontent.com/" + REPOSITORY + "/" + BRANCH + "/abnw-versions.json";
    }

    public static String releaseDownloadsPrefix() {
        return "https://github.com/" + REPOSITORY + "/releases/download/";
    }
}
