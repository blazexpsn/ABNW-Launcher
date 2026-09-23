package org.teamzetaverse.cosmetics.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class CosmeticRegistry {
    public static final int FORMAT_VERSION = 1;
    public static final String RESOURCE = "/META-INF/abnw-cosmetics/registry.json";
    public static final long MAX_ASSET_BYTES = 8L * 1024 * 1024;

    private static final Pattern ID = Pattern.compile("^[a-z0-9_]{1,32}:[a-z0-9_]{1,64}$");
    private static final Pattern ASSET_NAME = Pattern.compile("^[a-z0-9_]{1,32}$");
    private static final Pattern ASSET_PATH = Pattern.compile("^[a-z0-9_-]{1,64}(/[a-z0-9_-]{1,64}){0,6}\\.(png|json|ogg)$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern PARTICLE_ID = Pattern.compile("^[a-z0-9_.-]{1,32}:[a-z0-9_/.-]{1,64}$");
    private static final Pattern CURRENCY = Pattern.compile("^[a-z]{3}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private static volatile CosmeticRegistry bundled;

    private final int formatVersion;
    private final List<Cosmetic> cosmetics;
    private final Map<String, Cosmetic> byId;

    private CosmeticRegistry(final int formatVersion, final List<Cosmetic> cosmetics) {
        this.formatVersion = formatVersion;
        this.cosmetics = List.copyOf(cosmetics);
        Map<String, Cosmetic> index = new LinkedHashMap<>();
        for (Cosmetic cosmetic : cosmetics) {
            index.put(cosmetic.id(), cosmetic);
        }
        this.byId = Collections.unmodifiableMap(index);
    }

    public static CosmeticRegistry bundled() {
        CosmeticRegistry current = bundled;
        if (current == null) {
            synchronized (CosmeticRegistry.class) {
                current = bundled;
                if (current == null) {
                    current = loadBundled();
                    bundled = current;
                }
            }
        }
        return current;
    }

    private static CosmeticRegistry loadBundled() {
        try (InputStream in = CosmeticRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new InvalidRegistryException("The cosmetics registry " + RESOURCE + " is missing from the build.");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidRegistryException("The cosmetics registry could not be read: " + e.getMessage(), e);
        }
    }

    public static CosmeticRegistry parse(final String json) {
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new InvalidRegistryException("The cosmetics registry is not valid JSON: " + e.getMessage(), e);
        }
        if (!rootElement.isJsonObject()) {
            throw new InvalidRegistryException("The cosmetics registry must be a JSON object.");
        }
        JsonObject root = rootElement.getAsJsonObject();
        int format = integer(root, "formatVersion", "registry").orElseThrow(() -> new InvalidRegistryException("The cosmetics registry needs a formatVersion."));
        if (format < 1 || format > FORMAT_VERSION) {
            throw new InvalidRegistryException("The cosmetics registry uses format " + format + ", but this build understands up to " + FORMAT_VERSION + ".");
        }
        JsonArray entries = array(root, "cosmetics", "registry");
        List<Cosmetic> cosmetics = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            JsonElement element = entries.get(i);
            if (!element.isJsonObject()) {
                throw new InvalidRegistryException("Cosmetic #" + i + " must be a JSON object.");
            }
            Cosmetic cosmetic = cosmetic(element.getAsJsonObject(), i);
            if (seen.put(cosmetic.id(), Boolean.TRUE) != null) {
                throw new InvalidRegistryException("Cosmetic " + cosmetic.id() + " is registered twice.");
            }
            cosmetics.add(cosmetic);
        }
        return new CosmeticRegistry(format, cosmetics);
    }

    private static Cosmetic cosmetic(final JsonObject object, final int index) {
        String id = string(object, "id", "cosmetic #" + index).orElse("");
        if (!ID.matcher(id).matches()) {
            throw new InvalidRegistryException("Cosmetic #" + index + " has an invalid id \"" + id + "\" (expected namespace:name, lowercase).");
        }
        String name = string(object, "name", id).orElse("").strip();
        if (name.isEmpty() || name.length() > 64) {
            throw new InvalidRegistryException(id + " needs a name of 1 to 64 characters.");
        }
        String description = string(object, "description", id).orElse("").strip();
        if (description.length() > 280) {
            throw new InvalidRegistryException(id + " has a description longer than 280 characters.");
        }
        String typeId = string(object, "type", id).orElse("");
        CosmeticType type = CosmeticType.byId(typeId).orElseThrow(() -> new InvalidRegistryException(id + " has an unknown type \"" + typeId + "\"."));
        CosmeticUnlock unlock = unlock(object(object, "unlock", id).orElseThrow(() -> new InvalidRegistryException(id + " needs an unlock section.")), id);
        Map<String, CosmeticAsset> assets = assets(object(object, "assets", id).orElseGet(JsonObject::new), id, type);
        Optional<ParticleEffect> particle = object(object, "particle", id).map(p -> particle(p, id, type));
        if (type.isParticle() && particle.isEmpty()) {
            throw new InvalidRegistryException(id + " is a " + type.id() + " cosmetic and needs a particle section.");
        }
        if (!type.isParticle() && particle.isPresent()) {
            throw new InvalidRegistryException(id + " is a " + type.id() + " cosmetic and cannot have a particle section.");
        }
        return new Cosmetic(id, name, description, type, unlock, assets, particle);
    }

    private static CosmeticUnlock unlock(final JsonObject object, final String id) {
        boolean free = bool(object, "free", id);
        OptionalInt patreon = integer(object, "patreonMinCents", id);
        if (patreon.isPresent() && patreon.getAsInt() < 0) {
            throw new InvalidRegistryException(id + " has a negative patreonMinCents.");
        }
        Optional<CosmeticUnlock.Purchase> purchase = object(object, "purchase", id).map(p -> {
            int cents = integer(p, "priceCents", id).orElse(0);
            if (cents <= 0) {
                throw new InvalidRegistryException(id + " needs a positive priceCents.");
            }
            String currency = string(p, "currency", id).orElse("");
            if (!CURRENCY.matcher(currency).matches()) {
                throw new InvalidRegistryException(id + " needs a lowercase three-letter currency.");
            }
            return new CosmeticUnlock.Purchase(cents, currency);
        });
        if (!free && patreon.isEmpty() && purchase.isEmpty()) {
            throw new InvalidRegistryException(id + " cannot be unlocked: set free, patreonMinCents or purchase.");
        }
        if (free && (patreon.isPresent() || purchase.isPresent())) {
            throw new InvalidRegistryException(id + " is free, so it cannot also be a patron perk or a purchase.");
        }
        return new CosmeticUnlock(free, patreon, purchase);
    }

    private static Map<String, CosmeticAsset> assets(final JsonObject object, final String id, final CosmeticType type) {
        if (type.isParticle() && !object.entrySet().isEmpty()) {
            throw new InvalidRegistryException(id + " is a particle cosmetic, so it has no assets to download.");
        }
        Map<String, CosmeticAsset> assets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String name = entry.getKey();
            if (!ASSET_NAME.matcher(name).matches() || !type.allowedAssets().contains(name)) {
                throw new InvalidRegistryException(id + " has an asset \"" + name + "\" that a " + type.id() + " cannot use (allowed: " + type.allowedAssets() + ").");
            }
            if (!entry.getValue().isJsonObject()) {
                throw new InvalidRegistryException(id + " asset " + name + " must be an object.");
            }
            JsonObject asset = entry.getValue().getAsJsonObject();
            String where = id + " asset " + name;
            String path = string(asset, "path", where).orElse("");
            if (!ASSET_PATH.matcher(path).matches()) {
                throw new InvalidRegistryException(where + " has an invalid path \"" + path + "\".");
            }
            String expectedExtension = name.equals("model") || name.equals("animation") ? "json" : "png";
            if (!path.endsWith("." + expectedExtension)) {
                throw new InvalidRegistryException(where + " must be a ." + expectedExtension + " file.");
            }
            String sha256 = string(asset, "sha256", where).orElse("");
            if (!SHA256.matcher(sha256).matches()) {
                throw new InvalidRegistryException(where + " has no valid sha256. Run the hashCosmetics Gradle task.");
            }
            long size = integer(asset, "size", where).orElse(0);
            if (size <= 0 || size > MAX_ASSET_BYTES) {
                throw new InvalidRegistryException(where + " has an invalid size. Run the hashCosmetics Gradle task.");
            }
            assets.put(name, new CosmeticAsset(name, path, sha256, size));
        }
        for (String required : type.requiredAssets()) {
            if (!assets.containsKey(required)) {
                throw new InvalidRegistryException(id + " is a " + type.id() + " and needs a \"" + required + "\" asset.");
            }
        }
        return Collections.unmodifiableMap(assets);
    }

    private static ParticleEffect particle(final JsonObject object, final String id, final CosmeticType type) {
        String particle = string(object, "particle", id).orElse("");
        if (!PARTICLE_ID.matcher(particle).matches()) {
            throw new InvalidRegistryException(id + " has an invalid particle id \"" + particle + "\".");
        }
        String patternId = string(object, "pattern", id).orElse("");
        ParticleEffect.Pattern pattern = null;
        for (ParticleEffect.Pattern candidate : ParticleEffect.Pattern.values()) {
            if (candidate.id().equals(patternId)) {
                pattern = candidate;
            }
        }
        if (pattern == null || pattern.type() != type) {
            throw new InvalidRegistryException(id + " has pattern \"" + patternId + "\", which a " + type.id() + " cannot use.");
        }
        float perSecond = decimal(object, "perSecond", id, 4f);
        if (!(perSecond > 0f) || perSecond > ParticleEffect.MAX_PER_SECOND) {
            throw new InvalidRegistryException(id + " needs perSecond between 0 and " + ParticleEffect.MAX_PER_SECOND + ".");
        }
        OptionalInt color = OptionalInt.empty();
        Optional<String> colorText = string(object, "color", id);
        if (colorText.isPresent()) {
            if (!COLOR.matcher(colorText.get()).matches()) {
                throw new InvalidRegistryException(id + " has an invalid color (expected #RRGGBB).");
            }
            color = OptionalInt.of(Integer.parseInt(colorText.get().substring(1), 16));
        }
        float scale = decimal(object, "scale", id, 1f);
        if (!(scale > 0f) || scale > 4f) {
            throw new InvalidRegistryException(id + " needs a scale between 0 and 4.");
        }
        float spread = decimal(object, "spread", id, 0.5f);
        if (!(spread >= 0f) || spread > 3f) {
            throw new InvalidRegistryException(id + " needs a spread between 0 and 3.");
        }
        return new ParticleEffect(particle, pattern, perSecond, color, scale, spread);
    }

    private static Optional<String> string(final JsonObject object, final String key, final String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new InvalidRegistryException(where + ": " + key + " must be a string.");
        }
        return Optional.of(element.getAsString());
    }

    private static OptionalInt integer(final JsonObject object, final String key, final String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return OptionalInt.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new InvalidRegistryException(where + ": " + key + " must be a number.");
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new InvalidRegistryException(where + ": " + key + " must be a whole number.");
        }
        return OptionalInt.of((int)value);
    }

    private static float decimal(final JsonObject object, final String key, final String where, final float fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new InvalidRegistryException(where + ": " + key + " must be a number.");
        }
        return element.getAsFloat();
    }

    private static boolean bool(final JsonObject object, final String key, final String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new InvalidRegistryException(where + ": " + key + " must be true or false.");
        }
        return primitive.getAsBoolean();
    }

    private static Optional<JsonObject> object(final JsonObject object, final String key, final String where) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonObject()) {
            throw new InvalidRegistryException(where + ": " + key + " must be an object.");
        }
        return Optional.of(element.getAsJsonObject());
    }

    private static JsonArray array(final JsonObject object, final String key, final String where) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new InvalidRegistryException(where + ": " + key + " must be an array.");
        }
        return element.getAsJsonArray();
    }

    public int formatVersion() {
        return this.formatVersion;
    }

    public List<Cosmetic> all() {
        return this.cosmetics;
    }

    public Optional<Cosmetic> get(final String id) {
        return Optional.ofNullable(this.byId.get(id));
    }

    public boolean isEmpty() {
        return this.cosmetics.isEmpty();
    }
}
