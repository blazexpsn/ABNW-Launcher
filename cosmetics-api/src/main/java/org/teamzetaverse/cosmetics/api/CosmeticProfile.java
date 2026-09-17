package org.teamzetaverse.cosmetics.api;

import java.util.List;
import java.util.Optional;

public record CosmeticProfile(String uuid, boolean patron, List<String> cosmetics) {
    public boolean has(final String cosmeticId) {
        return this.cosmetics.contains(cosmeticId);
    }

    public List<Cosmetic> resolve(final CosmeticRegistry registry) {
        return this.cosmetics.stream().map(registry::get).flatMap(Optional::stream).toList();
    }
}
