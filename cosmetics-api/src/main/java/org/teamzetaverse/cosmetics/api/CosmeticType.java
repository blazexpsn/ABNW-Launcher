package org.teamzetaverse.cosmetics.api;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum CosmeticType {
    CAPE(Set.of("texture"), Set.of("texture", "emissive")),
    HAT(Set.of("model", "texture"), Set.of("model", "texture", "emissive")),
    BACK(Set.of("model", "texture"), Set.of("model", "texture", "emissive")),
    WINGS(Set.of("model", "texture"), Set.of("model", "texture", "emissive", "animation")),
    AURA(Set.of(), Set.of()),
    TRAIL(Set.of(), Set.of()),
    PENGUIN_HEAD(Set.of("texture"), Set.of("texture")),
    PENGUIN_FACE(Set.of("texture"), Set.of("texture")),
    PENGUIN_NECK(Set.of("texture"), Set.of("texture"));

    private final Set<String> requiredAssets;
    private final Set<String> allowedAssets;

    CosmeticType(final Set<String> requiredAssets, final Set<String> allowedAssets) {
        this.requiredAssets = requiredAssets;
        this.allowedAssets = allowedAssets;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean isPenguinAccessory() {
        return this == PENGUIN_HEAD || this == PENGUIN_FACE || this == PENGUIN_NECK;
    }

    public boolean isParticle() {
        return this == AURA || this == TRAIL;
    }

    public Set<String> requiredAssets() {
        return this.requiredAssets;
    }

    public Set<String> allowedAssets() {
        return this.allowedAssets;
    }

    public static Optional<CosmeticType> byId(final String id) {
        for (CosmeticType type : values()) {
            if (type.id().equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
