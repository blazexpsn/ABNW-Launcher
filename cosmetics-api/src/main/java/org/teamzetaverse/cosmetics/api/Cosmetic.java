package org.teamzetaverse.cosmetics.api;

import java.util.Map;
import java.util.Optional;

public record Cosmetic(String id, String name, String description, CosmeticType type, CosmeticUnlock unlock, Map<String, CosmeticAsset> assets,
                       Optional<ParticleEffect> particle) {
    public Optional<CosmeticAsset> asset(final String name) {
        return Optional.ofNullable(this.assets.get(name));
    }

    public boolean needsDownload() {
        return !this.assets.isEmpty();
    }
}
