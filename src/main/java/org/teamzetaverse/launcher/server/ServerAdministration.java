package org.teamzetaverse.launcher.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.update.UpdateChecker;
import org.teamzetaverse.launcher.util.FileMoves;
import org.teamzetaverse.launcher.util.Json;

/** Server files edited while stopped. Existing unrelated fields and player entries are retained. */
public final class ServerAdministration {
    public static final String PUBLIC_MIN_VERSION = "1.0.9.37";
    public static final String PACK_FILE = "abnw-launcher-permissions.zip";
    private static final String NODE_PATTERN = "(?:\\*|[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*(?:\\.\\*)?)";
    private static final String GROUP_PATTERN = "[a-z0-9_.-]+:[a-z0-9_-]+(?:/[a-z0-9_-]+)*";

    private ServerAdministration() {}

    public static boolean supportsPublic(final Release release) {
        return release != null && release.id != null && release.id.matches("[0-9]+(?:\\.[0-9]+){3}(?:[-+].*)?")
            && UpdateChecker.compare(release.id, PUBLIC_MIN_VERSION) >= 0;
    }

    public static Properties properties(final Instance instance) throws IOException {
        Properties result = new Properties();
        if (Files.isRegularFile(instance.serverPropertiesFile())) {
            result.load(new StringReader(Files.readString(instance.serverPropertiesFile(), StandardCharsets.UTF_8)));
        }
        return result;
    }

    public static Path worldFolder(final Instance instance) throws IOException {
        return worldFolder(instance, properties(instance).getProperty("level-name", "world"));
    }

    public static Path worldFolder(final Instance instance, final String name) throws IOException {
        Path root = instance.gameFolder().toAbsolutePath().normalize();
        Path target = root.resolve(name).normalize();
        if (name.isBlank() || target.equals(root) || !target.startsWith(root)) {
            throw new IOException("The world folder must be a folder inside this server's game folder.");
        }
        // A symlink must not send the permission editor into another server or directory.
        Path ancestor = target;
        while (!Files.exists(ancestor) && ancestor.getParent() != null) ancestor = ancestor.getParent();
        if (Files.exists(root) && !ancestor.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("The world folder points outside this server.");
        }
        return target;
    }

    public static void saveProperties(final Instance instance, final Properties properties) throws IOException {
        worldFolder(instance, properties.getProperty("level-name", "world"));
        StringWriter writer = new StringWriter();
        properties.store(writer, "ABNW server settings");
        Json.writeString(instance.serverPropertiesFile(), writer.toString());
    }

    public static JsonArray operators(final Instance instance) throws IOException {
        Path file = instance.gameFolder().resolve("ops.json");
        if (!Files.exists(file)) return new JsonArray();
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!json.isJsonArray()) throw new IllegalArgumentException("expected a list");
            JsonArray entries = json.getAsJsonArray();
            for (JsonElement entry : entries) {
                java.util.UUID.fromString(entry.getAsJsonObject().get("uuid").getAsString());
            }
            return entries;
        } catch (RuntimeException e) {
            throw new IOException("Cannot edit ops.json: " + e.getMessage(), e);
        }
    }

    public static void putOperator(final JsonArray operators, final PlayerProfiles.Profile player, final int level, final boolean bypass) {
        if (level < 1 || level > 4) throw new IllegalArgumentException("Operator level must be 1–4");
        JsonObject entry = null;
        for (JsonElement existing : operators) {
            if (player.uuid().toString().equalsIgnoreCase(Json.string(existing.getAsJsonObject(), "uuid"))) {
                entry = existing.getAsJsonObject();
                break;
            }
        }
        if (entry == null) {
            entry = new JsonObject();
            operators.add(entry);
        }
        entry.addProperty("uuid", player.uuid().toString());
        entry.addProperty("name", player.name());
        entry.addProperty("level", level);
        entry.addProperty("bypassesPlayerLimit", bypass);
    }

    public static void saveOperators(final Instance instance, final JsonArray operators) throws IOException {
        Json.write(instance.gameFolder().resolve("ops.json"), operators);
    }

    public static JsonObject players(final Instance instance) throws IOException {
        Path file = worldFolder(instance).resolve("permissions.json");
        JsonObject root = Files.exists(file) ? Json.readObject(file) : new JsonObject();
        if (!root.has("players")) root.add("players", new JsonObject());
        if (!root.get("players").isJsonObject()) throw new IOException("permissions.json has an invalid players field.");
        return root;
    }

    public static void putPlayer(final JsonObject root, final PlayerProfiles.Profile player, final List<String> groups, final List<String> nodes) {
        groups.forEach(ServerAdministration::groupId);
        JsonObject players = root.getAsJsonObject("players");
        JsonObject entry = players.has(player.uuid().toString()) ? players.getAsJsonObject(player.uuid().toString()) : new JsonObject();
        entry.addProperty("name", player.name());
        JsonArray groupArray = new JsonArray();
        groups.stream().distinct().forEach(groupArray::add);
        entry.add("groups", groupArray);
        entry.add("permissions", nodeArray(nodes));
        players.add(player.uuid().toString(), entry);
    }

    public static void savePlayers(final Instance instance, final JsonObject root) throws IOException {
        Json.write(worldFolder(instance).resolve("permissions.json"), root);
    }

    public static String groupId(final String id) {
        if (!id.matches(GROUP_PATTERN)) throw new IllegalArgumentException("Use a group id such as myserver:builder (lowercase).");
        return id;
    }

    public static List<String> lines(final String text) {
        return text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    public static JsonArray nodeArray(final List<String> nodes) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String input : nodes) {
            String raw = input.trim().toLowerCase(Locale.ROOT);
            String node = raw.startsWith("-") ? raw.substring(1) : raw;
            if (!node.matches(NODE_PATTERN)) throw new IllegalArgumentException("Invalid permission node: " + input);
            normalized.put(node, raw);
        }
        JsonArray result = new JsonArray();
        normalized.values().forEach(result::add);
        return result;
    }

    public static JsonObject group(final List<String> inherits, final int weight, final int applies, final List<String> nodes) {
        JsonObject group = new JsonObject();
        JsonArray parents = new JsonArray();
        inherits.stream().distinct().forEach(id -> parents.add(groupId(id)));
        group.add("inherits", parents);
        group.addProperty("weight", weight);
        if (applies > 0) {
            JsonObject to = new JsonObject();
            if (applies == 1) to.addProperty("everyone", true);
            else to.addProperty("op_level", applies - 1);
            group.add("applies_to", to);
        }
        group.add("permissions", nodeArray(nodes));
        return group;
    }

    public static JsonObject bundledCatalog() throws IOException {
        try (InputStream in = ServerAdministration.class.getResourceAsStream("/server/nodes.json")) {
            if (in == null) throw new IOException("The launcher permission catalog is missing.");
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static JsonObject catalog(final Instance instance) throws IOException {
        JsonObject bundled = bundledCatalog();
        Path runtime = instance.gameFolder().resolve("permission-nodes.json");
        if (!Files.isRegularFile(runtime)) return bundled;
        JsonObject current = Json.readObject(runtime);
        if (current.has("nodes") && current.get("nodes").isJsonArray()) {
            Map<String, JsonElement> nodes = new java.util.TreeMap<>();
            for (JsonElement node : bundled.getAsJsonArray("nodes")) nodes.put(node.getAsJsonObject().get("node").getAsString(), node);
            for (JsonElement node : current.getAsJsonArray("nodes")) nodes.put(node.getAsJsonObject().get("node").getAsString(), node);
            JsonArray merged = new JsonArray();
            nodes.values().forEach(merged::add);
            bundled.add("nodes", merged);
        }
        return bundled;
    }

    public static Map<String, JsonObject> groups(final Instance instance, final Path gameJar) throws IOException {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        JsonObject defaults = bundledCatalog().getAsJsonObject("groups");
        defaults.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsJsonObject().deepCopy()));
        if (gameJar != null && Files.isRegularFile(gameJar)) readGroups(gameJar, result);
        Path pack = worldFolder(instance).resolve("datapacks").resolve(PACK_FILE);
        if (Files.isRegularFile(pack)) readGroups(pack, result);
        return result;
    }

    private static void readGroups(final Path file, final Map<String, JsonObject> into) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (!path.matches("data/[a-z0-9_.-]+/permission_group/[a-z0-9_/-]+\\.json")) continue;
                String[] parts = path.split("/", 4);
                String id = groupId(parts[1] + ":" + parts[3].substring(0, parts[3].length() - 5));
                try (InputStream in = zip.getInputStream(entry)) {
                    into.put(id, Json.parseObject(new String(in.readNBytes(1_048_577), StandardCharsets.UTF_8)));
                }
            }
        }
    }

    public static void validateGroups(final Map<String, JsonObject> groups) {
        for (var entry : groups.entrySet()) {
            groupId(entry.getKey());
            JsonObject group = entry.getValue();
            if (group.has("permissions")) nodeArray(strings(group.getAsJsonArray("permissions")));
            if (group.has("inherits")) strings(group.getAsJsonArray("inherits")).forEach(ServerAdministration::groupId);
            if (reaches(groups, entry.getKey(), entry.getKey(), new HashSet<>(), true)) {
                throw new IllegalArgumentException("Group inheritance contains a loop involving " + entry.getKey());
            }
        }
    }

    private static boolean reaches(final Map<String, JsonObject> groups, final String from, final String target, final Set<String> seen, final boolean first) {
        if (!first && from.equals(target)) return true;
        if (!seen.add(from) || !groups.containsKey(from)) return false;
        JsonArray parents = groups.get(from).getAsJsonArray("inherits");
        if (parents == null) return false;
        for (String parent : strings(parents)) if (reaches(groups, parent, target, seen, false)) return true;
        return false;
    }

    public static List<String> strings(final JsonArray array) {
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        array.forEach(entry -> result.add(entry.getAsString()));
        return result;
    }

    public static Path saveGroups(final Instance instance, final Path gameJar, final Map<String, JsonObject> groups) throws IOException {
        validateGroups(groups);
        JsonObject format = null;
        if (gameJar != null && Files.isRegularFile(gameJar)) {
            try (ZipFile zip = new ZipFile(gameJar.toFile())) {
                ZipEntry version = zip.getEntry("version.json");
                if (version != null) try (InputStream in = zip.getInputStream(version)) {
                    format = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject("pack_version");
                }
            }
        }
        if (format == null && !"26.1.2".equals(instance.release.minecraft)) {
            throw new IOException("Install this server's build first so its datapack format can be read.");
        }
        int major = format == null ? 101 : format.get("data_major").getAsInt();
        int minor = format == null ? 1 : format.get("data_minor").getAsInt();
        JsonArray packFormat = new JsonArray();
        packFormat.add(major); packFormat.add(minor);
        JsonObject metadata = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Permission groups managed by the ABNW Launcher");
        pack.add("min_format", packFormat);
        pack.add("max_format", packFormat.deepCopy());
        metadata.add("pack", pack);
        Path target = worldFolder(instance).resolve("datapacks").resolve(PACK_FILE);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "permissions-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                writeEntry(zip, "pack.mcmeta", metadata);
                for (var entry : groups.entrySet()) {
                    String[] id = entry.getKey().split(":", 2);
                    writeEntry(zip, "data/" + id[0] + "/permission_group/" + id[1] + ".json", entry.getValue());
                }
            }
            FileMoves.replace(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    private static void writeEntry(final ZipOutputStream zip, final String path, final JsonObject json) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
