package org.teamzetaverse.cosmetics.api;

import java.util.Locale;

public record CosmeticAsset(String name, String path, String sha256, long size) {
    public String extension() {
        int dot = this.path.lastIndexOf('.');
        return dot < 0 ? "" : this.path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public String mediaType() {
        return switch (this.extension()) {
            case "png" -> "image/png";
            case "json" -> "application/json";
            case "ogg" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }
}
