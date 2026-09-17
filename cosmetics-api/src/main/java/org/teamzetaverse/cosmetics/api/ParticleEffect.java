package org.teamzetaverse.cosmetics.api;

import java.util.Locale;
import java.util.OptionalInt;

public record ParticleEffect(String particle, Pattern pattern, float perSecond, OptionalInt color, float scale, float spread) {
    public enum Pattern {
        FOOTSTEPS(CosmeticType.TRAIL),
        TRAIL(CosmeticType.TRAIL),
        ORBIT(CosmeticType.AURA),
        HALO(CosmeticType.AURA),
        AMBIENT(CosmeticType.AURA);

        private final CosmeticType type;

        Pattern(final CosmeticType type) {
            this.type = type;
        }

        public CosmeticType type() {
            return this.type;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final float MAX_PER_SECOND = 40f;
}
