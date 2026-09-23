package org.teamzetaverse.cosmetics.api;

import java.util.Optional;
import java.util.OptionalInt;

public record CosmeticUnlock(boolean free, OptionalInt patreonMinCents, Optional<Purchase> purchase) {
    /** The registry price is sent to Stripe as an ephemeral price_data value. */
    public record Purchase(int priceCents, String currency) {
    }

    public boolean patronPerk() {
        return this.patreonMinCents.isPresent();
    }

    public boolean purchasable() {
        return this.purchase.isPresent();
    }
}
