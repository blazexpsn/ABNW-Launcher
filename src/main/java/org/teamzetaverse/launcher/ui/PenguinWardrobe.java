package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.teamzetaverse.cosmetics.api.Cosmetic;
import org.teamzetaverse.cosmetics.api.CosmeticAsset;
import org.teamzetaverse.cosmetics.api.CosmeticRegistry;
import org.teamzetaverse.cosmetics.api.CosmeticsService;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.instance.Instance;

final class PenguinWardrobe {
    static final String[] SLOTS = {"head", "face", "neck"};
    static final String[] SLOT_LABELS = {"Head", "Face", "Neck"};
    static final String NONE = "none";

    private static final String PORTRAIT = "resource:/assets/ui/penguin/portrait.png";
    private static final float PORTRAIT_W = 26f;
    private static final float PORTRAIT_H = 30f;
    private static final float BUST_X = 1f;
    private static final float BUST = 23f;
    private static final long RETRY_MILLIS = 10L * 60 * 1000;

    record Accessory(String slot, String id, String name, String description, Cosmetic cosmetic, String texture) {
        boolean free() {
            return this.cosmetic == null;
        }

        String price() {
            if (this.cosmetic == null || this.cosmetic.unlock().purchase().isEmpty()) {
                return "";
            }
            var purchase = this.cosmetic.unlock().purchase().get();
            String amount = String.format(Locale.ROOT, "%d.%02d", purchase.priceCents() / 100, purchase.priceCents() % 100);
            return switch (purchase.currency()) {
                case "usd" -> "$" + amount;
                case "gbp" -> "£" + amount;
                case "eur" -> "€" + amount;
                default -> amount + " " + purchase.currency().toUpperCase(Locale.ROOT);
            };
        }
    }

    private static final List<Accessory> BUILT_IN = List.of(
        builtIn("head", "top_hat", "Top hat"),
        builtIn("head", "party_hat", "Party hat"),
        builtIn("head", "beanie", "Beanie"),
        builtIn("face", "sunglasses", "Sunglasses"),
        builtIn("face", "monocle", "Monocle"),
        builtIn("neck", "abnw_scarf", "ABNW scarf"),
        builtIn("neck", "red_scarf", "Red scarf"),
        builtIn("neck", "bow_tie", "Bow tie"));

    private static volatile PenguinWardrobe current;

    private final LauncherUi ui;
    private volatile List<Accessory> store = List.of();
    private volatile boolean storeLoading;
    private volatile long storeAttempt;
    private String equippedFor = "";
    private com.google.gson.JsonObject equipped = new com.google.gson.JsonObject();

    PenguinWardrobe(final LauncherUi ui) {
        this.ui = ui;
        current = this;
    }

    static PenguinWardrobe current() {
        return current;
    }

    private static Accessory builtIn(final String slot, final String id, final String name) {
        return new Accessory(slot, id, name, "Free", null, "resource:/assets/ui/penguin/" + slot + "/" + id + ".png");
    }

    static String defaultFor(final String slot) {
        return "neck".equals(slot) ? "abnw_scarf" : NONE;
    }

    List<Accessory> accessories(final String slot) {
        this.ensureStore();
        List<Accessory> result = new ArrayList<>();
        for (Accessory accessory : BUILT_IN) {
            if (accessory.slot().equals(slot)) {
                result.add(accessory);
            }
        }
        for (Accessory accessory : this.store) {
            if (accessory.slot().equals(slot)) {
                result.add(accessory);
            }
        }
        return result;
    }

    List<Accessory> store() {
        this.ensureStore();
        return this.store;
    }

    boolean storeLoading() {
        return this.storeLoading;
    }

    boolean owns(final Accessory accessory) {
        return accessory.free() || this.ui.unlockedCosmetics().contains(accessory.id());
    }

    Optional<Accessory> find(final String slot, final String id) {
        for (Accessory accessory : this.accessories(slot)) {
            if (accessory.id().equals(id)) {
                return Optional.of(accessory);
            }
        }
        return Optional.empty();
    }

    String chosenId(final Instance instance, final String slot) {
        String id = this.ui.cosmetics.identities().selected();
        this.loadEquipped(id);
        String chosen = id.isEmpty() ? (instance.penguin == null ? null : instance.penguin.get(slot))
            : org.teamzetaverse.launcher.util.Json.string(org.teamzetaverse.launcher.util.Json.object(this.equipped, instance.id), slot);
        return chosen == null ? defaultFor(slot) : chosen;
    }

    Optional<Accessory> worn(final Instance instance, final String slot) {
        String id = this.chosenId(instance, slot);
        if (NONE.equals(id)) {
            return Optional.empty();
        }
        return this.find(slot, id).filter(this::owns);
    }

    void wear(final Instance instance, final String slot, final String id) {
        if (!NONE.equals(id) && this.find(slot, id).filter(this::owns).isEmpty()) return;
        String identity = this.ui.cosmetics.identities().selected();
        this.loadEquipped(identity);
        if (identity.isEmpty()) { instance.penguin.put(slot, id); return; }
        var choices = org.teamzetaverse.launcher.util.Json.object(this.equipped, instance.id);
        if (choices == null) { choices = new com.google.gson.JsonObject(); this.equipped.add(instance.id, choices); }
        choices.addProperty(slot, id);
        try { org.teamzetaverse.launcher.util.Json.writeString(this.ui.cosmetics.identityDirectory(identity).resolve("equipped.json"), this.equipped.toString()); }
        catch (java.io.IOException e) { this.ui.fail(new java.io.IOException("Could not save equipped cosmetics.")); }
    }

    private void loadEquipped(String id) {
        if (id.equals(this.equippedFor)) return;
        this.equippedFor = id;
        this.equipped = new com.google.gson.JsonObject();
        if (id.isEmpty()) return;
        Path file = this.ui.cosmetics.identityDirectory(id).resolve("equipped.json");
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 1024 * 1024) {
                this.equipped = org.teamzetaverse.launcher.util.Json.parseObject(Files.readString(file));
            }
        } catch (java.io.IOException | RuntimeException e) { this.ui.fail(new java.io.IOException("Could not load equipped cosmetics.")); }
    }

    List<String> look(final Instance instance) {
        List<String> textures = new ArrayList<>();
        for (String slot : new String[]{"neck", "face", "head"}) {
            this.worn(instance, slot).ifPresent(accessory -> textures.add(accessory.texture()));
        }
        return textures;
    }

    ImageCache.Texture customIcon(final Instance instance) {
        Path file = instance.customIcon();
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        return this.ui.images.get("file:" + file.toAbsolutePath(), 256);
    }

    static void drawBust(final ImDrawList dl, final float x, final float y, final float box, final List<String> textures) {
        ImageCache.Texture portrait = Pixel.texture(PORTRAIT);
        if (portrait == null) {
            return;
        }
        float scale = Math.max(1f, (float)Math.floor(box / BUST));
        float size = BUST * scale;
        float px0 = Math.round(x + (box - size) * 0.5f);
        float py0 = Math.round(y + box - size);
        float u0 = BUST_X / PORTRAIT_W;
        float u1 = (BUST_X + BUST) / PORTRAIT_W;
        float v1 = BUST / PORTRAIT_H;
        dl.addImage(portrait.id(), px0, py0, px0 + size, py0 + size, u0, 0f, u1, v1, u32(0xFFFFFF));
        for (String key : textures) {
            ImageCache.Texture texture = Pixel.texture(key);
            if (texture != null) {
                dl.addImage(texture.id(), px0, py0, px0 + size, py0 + size, u0, 0f, u1, v1, u32(0xFFFFFF));
            }
        }
    }

    static void drawFull(final ImDrawList dl, final float x, final float y, final float box, final List<String> textures) {
        ImageCache.Texture portrait = Pixel.texture(PORTRAIT);
        if (portrait == null) {
            return;
        }
        float scale = Math.max(1f, (float)Math.floor(box / PORTRAIT_H));
        float w = PORTRAIT_W * scale;
        float h = PORTRAIT_H * scale;
        float px0 = Math.round(x + (box - w) * 0.5f);
        float py0 = Math.round(y + (box - h) * 0.5f);
        dl.addImage(portrait.id(), px0, py0, px0 + w, py0 + h, 0f, 0f, 1f, 1f, u32(0xFFFFFF));
        for (String key : textures) {
            ImageCache.Texture texture = Pixel.texture(key);
            if (texture != null) {
                dl.addImage(texture.id(), px0, py0, px0 + w, py0 + h, 0f, 0f, 1f, 1f, u32(0xFFFFFF));
            }
        }
    }

    private void ensureStore() {
        if (!BuildInfo.cosmeticsConfigured() || this.storeLoading
            || System.currentTimeMillis() - this.storeAttempt < RETRY_MILLIS) {
            return;
        }
        this.storeLoading = true;
        this.storeAttempt = System.currentTimeMillis();
        Path cache = this.ui.paths.cache().resolve("penguin-wardrobe");
        Thread thread = new Thread(() -> {
            try {
                CosmeticsService service = new CosmeticsService(BuildInfo.COSMETICS_API_URL, "ABNWLauncher/" + BuildInfo.VERSION);
                try { this.loadAssets(service, service.cachedRegistry(cache), cache, false); }
                catch (java.io.IOException | RuntimeException ignored) { /* First synchronization has no cached registry. */ }
                this.loadAssets(service, service.synchronizeRegistry(cache), cache, true);
            } catch (Exception e) {
                System.err.println("Could not load the penguin wardrobe: " + e.getMessage());
            } finally {
                this.storeLoading = false;
            }
        }, "ABNW penguin wardrobe");
        thread.setDaemon(true);
        thread.start();
    }
    private void loadAssets(CosmeticsService service, CosmeticRegistry registry, Path cache, boolean online) {
        java.util.Map<String, Accessory> loaded = new java.util.LinkedHashMap<>();
        // Retain usable assets if a partial synchronization fails.
        for (Accessory item : this.store) loaded.put(item.id(), item);
        for (Cosmetic cosmetic : registry.all()) {
            try {
                if (online) for (CosmeticAsset asset : cosmetic.assets().values()) service.download(cosmetic, asset, cache);
                if (!cosmetic.type().isPenguinAccessory()) continue;
                Optional<CosmeticAsset> texture = cosmetic.asset("texture");
                if (texture.isEmpty()) continue;
                Optional<Path> file = service.cachedAsset(texture.get(), cache);
                if (file.isEmpty()) continue;
                String slot = cosmetic.type().id().substring("penguin_".length());
                loaded.put(cosmetic.id(), new Accessory(slot, cosmetic.id(), cosmetic.name(), cosmetic.description(), cosmetic,
                    "file:" + file.get().toAbsolutePath()));
            } catch (java.io.IOException e) { /* Missing assets fall back; never discard valid ownership. */ }
        }
        this.store = List.copyOf(loaded.values());
    }

}
