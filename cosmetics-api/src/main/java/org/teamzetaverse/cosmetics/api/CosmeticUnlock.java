package org.teamzetaverse.cosmetics.api;

import java.util.Optional;
import java.util.OptionalInt;

public record CosmeticUnlock(boolean free, OptionalInt patreonMinCents, Optional<Purchase> purchase) {
    public record Purchase(String stripePriceId, int priceCents, String currency) {
    }

    public boolean patronPerk() {
        return this.patreonMinCents.isPresent();
    }

    public boolean purchasable() {
        return this.purchase.isPresent();
    }
}
